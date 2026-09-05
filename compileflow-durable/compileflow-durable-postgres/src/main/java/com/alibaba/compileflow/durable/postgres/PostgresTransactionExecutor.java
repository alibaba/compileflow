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

    private static void rollback(Connection connection, Throwable original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
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
        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (Error failure) {
                rollback(connection, failure);
                throw failure;
            } catch (Exception failure) {
                rollback(connection, failure);
                if (failure instanceof DurableProcessException durable) {
                    throw durable;
                }
                if (failure instanceof SQLException sqlFailure) {
                    throw failureMapper.apply(sqlFailure);
                }
                if (failure instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException("Unexpected Durable Store failure", failure);
            } finally {
                restoreAutoCommit(connection, originalAutoCommit);
            }
        } catch (DurableProcessException failure) {
            throw failure;
        } catch (SQLException failure) {
            throw failureMapper.apply(failure);
        }
    }

    @FunctionalInterface
    interface SqlWork<T> {
        T execute(Connection connection) throws SQLException;
    }
}
