/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.durable.postgres;

import com.alibaba.compileflow.durable.api.error.DurableErrorCode;
import com.alibaba.compileflow.durable.api.error.DurableProcessException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Function;
import javax.sql.DataSource;

/**
 * Owns JDBC connection and transaction lifecycle for one PostgreSQL Store.
 *
 * <p>The caller still defines the complete atomic Store operation. This
 * collaborator only guarantees one connection, one commit or rollback, and
 * deterministic exception translation; it never starts nested work.</p>
 *
 * @author yusu
 */
final class PostgresTransactionExecutor {
    private final DataSource dataSource;
    private final Function<SQLException, DurableProcessException> failureMapper;

    PostgresTransactionExecutor(DataSource dataSource) {
        this(dataSource, PostgresTransactionExecutor::storeFailure);
    }

    PostgresTransactionExecutor(DataSource dataSource, Function<SQLException, DurableProcessException> failureMapper) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.failureMapper = Objects.requireNonNull(failureMapper, "failureMapper");
    }

    private static boolean rollback(Connection connection, Throwable original) {
        try {
            connection.rollback();
            return true;
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
            return false;
        }
    }

    private static void restoreAutoCommit(Connection connection, boolean autoCommit) {
        try {
            connection.setAutoCommit(autoCommit);
        } catch (SQLException ignored) {
            // Connection is closing; preserve the operation outcome.
        }
    }

    private static DurableProcessException storeFailure(SQLException failure) {
        return DurableProcessException.of(DurableErrorCode.STORE_UNAVAILABLE, "Durable PostgreSQL operation failed",
                failure);
    }

    <T> T withConnection(SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            return work.execute(connection);
        } catch (DurableProcessException failure) {
            throw failure;
        } catch (SQLException failure) {
            throw failureMapper.apply(failure);
        }
    }

    <T> T inTransaction(SqlWork<T> work) {
        return withConnection(connection -> {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            boolean transactionResolved = false;
            try {
                T result = work.execute(connection);
                connection.commit();
                transactionResolved = true;
                return result;
            } catch (SQLException | RuntimeException | Error failure) {
                transactionResolved = rollback(connection, failure);
                throw failure;
            } finally {
                // Enabling auto-commit after a failed rollback can commit partial work.
                if (transactionResolved) {
                    restoreAutoCommit(connection, originalAutoCommit);
                }
            }
        });
    }

    @FunctionalInterface
    interface SqlWork<T> {
        T execute(Connection connection) throws SQLException;
    }
}
