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
package com.alibaba.compileflow.durable.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.durable.api.error.DurableErrorCode;
import com.alibaba.compileflow.durable.api.error.DurableProcessException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class MySqlTransactionExecutorTest {
    @Test
    void preservesDurableFailureIdentityAndRollbackEvidence() {
        TrackingConnection tracking = new TrackingConnection();
        tracking.rollbackFailure = new SQLException("rollback failed");
        var executor = new MySqlTransactionExecutor(new SingleConnectionDataSource(tracking.connection()));
        var failure = DurableProcessException.of(DurableErrorCode.INVALID_ARGUMENT, "work failed");
        assertThatThrownBy(() -> executor.inTransaction(connection -> {
            throw failure;
        })).isSameAs(failure);
        assertThat(failure.getSuppressed()).containsExactly(tracking.rollbackFailure);
        assertThat(tracking.closed).isTrue();
    }

    @Test
    void mapsSqlFailureOnceAfterConnectionCleanupWithAllEvidence() {
        TrackingConnection tracking = new TrackingConnection();
        tracking.rollbackFailure = new SQLException("rollback failed");
        tracking.closeFailure = new SQLException("close failed");
        SQLException original = new SQLException("work failed");
        int[] mappings = {0};
        var executor =
                new MySqlTransactionExecutor(new SingleConnectionDataSource(tracking.connection()), failure -> {
            mappings[0]++;
            assertThat(tracking.closed).isTrue();
            assertThat((Object) failure).isSameAs(original);
            assertThat(failure.getSuppressed()).containsExactly(tracking.rollbackFailure, tracking.closeFailure);
            return DurableProcessException.of(DurableErrorCode.STORE_UNAVAILABLE, "mapped", failure);
        });
        assertThatThrownBy(() -> executor.inTransaction(connection -> {
            throw original;
        }))
            .isInstanceOf(DurableProcessException.class)
            .hasCause(original);
        assertThat(mappings[0]).isOne();
    }

    @Test
    void scopesUtcToBorrowedReadConnection() {
        TrackingConnection tracking = new TrackingConnection();
        MySqlTransactionExecutor executor =
                new MySqlTransactionExecutor(new SingleConnectionDataSource(tracking.connection()));
        executor.withConnection(connection -> {
            assertThat(tracking.timeZone).isEqualTo("+00:00");
            return null;
        });
        assertThat(tracking.timeZone).isEqualTo("+08:00");
        assertThat(tracking.closed).isTrue();
    }

    @Test
    void scopesUtcToTransactionAndRestoresItOnFailure() {
        TrackingConnection tracking = new TrackingConnection();
        MySqlTransactionExecutor executor =
                new MySqlTransactionExecutor(new SingleConnectionDataSource(tracking.connection()));
        AssertionError failure = new AssertionError("work");
        assertThatThrownBy(() -> executor.inTransaction(connection -> {
            assertThat(tracking.timeZone).isEqualTo("+00:00");
            throw failure;
        })).isSameAs(failure);
        assertThat(tracking.timeZone).isEqualTo("+08:00");
        assertThat(tracking.rollbackCount).isOne();
        assertThat(tracking.closed).isTrue();
    }

    @Test
    void failedTimeZoneRestoreAbortsConnectionAndPreservesWorkFailure() {
        TrackingConnection tracking = new TrackingConnection();
        tracking.timeZoneRestoreFailure = new SQLException("restore failed");
        var executor = new MySqlTransactionExecutor(new SingleConnectionDataSource(tracking.connection()));
        var failure = new IllegalStateException("work failed");
        assertThatThrownBy(() -> executor.withConnection(connection -> {
            throw failure;
        })).isSameAs(failure);
        assertThat(failure.getSuppressed()).containsExactly(tracking.timeZoneRestoreFailure);
        assertThat(tracking.aborted).isTrue();
        assertThat(tracking.closed).isTrue();
    }

    @Test
    void ambiguousTimeZoneSetupFailureStillRestoresTheBorrowedSession() {
        TrackingConnection tracking = new TrackingConnection();
        tracking.timeZoneSetupFailure = new SQLException("setup acknowledgement lost");
        var executor = new MySqlTransactionExecutor(new SingleConnectionDataSource(tracking.connection()));
        assertThatThrownBy(() -> executor.withConnection(connection -> {
            throw new AssertionError("work must not run after setup failure");
        }))
            .isInstanceOf(DurableProcessException.class)
            .hasCause(tracking.timeZoneSetupFailure);
        assertThat(tracking.timeZone).isEqualTo("+08:00");
        assertThat(tracking.closed).isTrue();
    }

    @Test
    void failedTimeZoneRestoreAfterSuccessIsReportedAndAbortsConnection() {
        TrackingConnection tracking = new TrackingConnection();
        tracking.timeZoneRestoreFailure = new SQLException("restore failed");
        var executor = new MySqlTransactionExecutor(new SingleConnectionDataSource(tracking.connection()));
        assertThatThrownBy(() -> executor.withConnection(connection -> "done"))
            .isInstanceOf(DurableProcessException.class)
            .hasCause(tracking.timeZoneRestoreFailure);
        assertThat(tracking.aborted).isTrue();
        assertThat(tracking.closed).isTrue();
    }

    @Test
    void commitsAndRestoresConnectionState() {
        TrackingConnection tracking = new TrackingConnection();
        MySqlTransactionExecutor executor =
                new MySqlTransactionExecutor(new SingleConnectionDataSource(tracking.connection()));

        String result = executor.inTransaction(connection -> {
            assertThat(connection.getAutoCommit()).isFalse();
            return "committed";
        });

        assertThat(result).isEqualTo("committed");
        assertThat(tracking.commitCount).isOne();
        assertThat(tracking.rollbackCount).isZero();
        assertThat(tracking.autoCommit).isTrue();
        assertThat(tracking.closed).isTrue();
    }

    @Test
    void rollsBackRuntimeFailureAndPreservesIdentity() {
        TrackingConnection tracking = new TrackingConnection();
        MySqlTransactionExecutor executor =
                new MySqlTransactionExecutor(new SingleConnectionDataSource(tracking.connection()));
        IllegalStateException failure = new IllegalStateException("failed");

        assertThatThrownBy(() -> executor.inTransaction(connection -> {
            throw failure;
        })).isSameAs(failure);

        assertThat(tracking.commitCount).isZero();
        assertThat(tracking.rollbackCount).isOne();
        assertThat(tracking.autoCommit).isTrue();
        assertThat(tracking.closed).isTrue();
    }

    @Test
    void rollsBackErrorAndPreservesIdentity() {
        TrackingConnection tracking = new TrackingConnection();
        MySqlTransactionExecutor executor =
                new MySqlTransactionExecutor(new SingleConnectionDataSource(tracking.connection()));
        AssertionError failure = new AssertionError("fatal");

        assertThatThrownBy(() -> executor.inTransaction(connection -> {
            throw failure;
        })).isSameAs(failure);

        assertThat(tracking.rollbackCount).isOne();
        assertThat(tracking.autoCommit).isTrue();
        assertThat(tracking.closed).isTrue();
    }

    @Test
    void translatesSqlFailureAndKeepsRollbackFailure() {
        TrackingConnection tracking = new TrackingConnection();
        SQLException rollbackFailure = new SQLException("rollback failed");
        tracking.rollbackFailure = rollbackFailure;
        MySqlTransactionExecutor executor =
                new MySqlTransactionExecutor(new SingleConnectionDataSource(tracking.connection()));
        SQLException sqlFailure = new SQLException("work failed");

        assertThatThrownBy(() -> executor.inTransaction(connection -> {
            throw sqlFailure;
        }))
            .isInstanceOfSatisfying(DurableProcessException.class, failure -> {
                assertThat(failure.getErrorCode()).isEqualTo(DurableErrorCode.STORE_UNAVAILABLE);
                assertThat(failure.getCause()).isSameAs(sqlFailure);
                assertThat(sqlFailure.getSuppressed()).containsExactly(rollbackFailure);
            });

        assertThat(tracking.rollbackCount).isOne();
        assertThat(tracking.autoCommit).isFalse();
        assertThat(tracking.commitCount).isZero();
        assertThat(tracking.closed).isTrue();
    }

    @Test
    void translatesConnectionFailure() {
        SQLException connectionFailure = new SQLException("connection unavailable");
        MySqlTransactionExecutor executor = new MySqlTransactionExecutor(new FailingDataSource(connectionFailure));

        assertThatThrownBy(() -> executor.withConnection(connection -> "unreachable"))
            .isInstanceOfSatisfying(DurableProcessException.class, failure -> {
                assertThat(failure.getErrorCode()).isEqualTo(DurableErrorCode.STORE_UNAVAILABLE);
                assertThat(failure.getCause()).isSameAs(connectionFailure);
            });
    }

    private static final class TrackingConnection implements InvocationHandler {
        private boolean autoCommit = true;
        private boolean closed;
        private int commitCount;
        private int rollbackCount;
        private SQLException rollbackFailure;
        private SQLException closeFailure;
        private String timeZone = "+08:00";
        private SQLException timeZoneRestoreFailure;
        private SQLException timeZoneSetupFailure;
        private boolean aborted;

        private static Object defaultValue(Class<?> returnType) {
            if (!returnType.isPrimitive()) {
                return null;
            }
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == char.class) {
                return '\0';
            }
            if (returnType == byte.class) {
                return (byte) 0;
            }
            if (returnType == short.class) {
                return (short) 0;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType == float.class) {
                return 0F;
            }
            if (returnType == double.class) {
                return 0D;
            }
            return null;
        }

        private Connection connection() {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class}, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            return switch (method.getName()) {
                case "prepareStatement" -> timeZoneStatement((String) arguments[0]);
                case "abort" -> {
                    aborted = true;
                    yield null;
                }
                case "getAutoCommit" -> autoCommit;
                case "setAutoCommit" -> {
                    autoCommit = (boolean) arguments[0];
                    yield null;
                }
                case "commit" -> {
                    commitCount++;
                    yield null;
                }
                case "rollback" -> {
                    rollbackCount++;
                    if (rollbackFailure != null) {
                        throw rollbackFailure;
                    }
                    yield null;
                }
                case "close" -> {
                    closed = true;
                    if (closeFailure != null) {
                        throw closeFailure;
                    }
                    yield null;
                }
                case "isClosed" -> closed;
                case "isWrapperFor" -> false;
                case "unwrap" -> throw new SQLException("not a wrapper");
                case "toString" -> "TrackingConnection";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> defaultValue(method.getReturnType());
            };
        }

        private PreparedStatement timeZoneStatement(String sql) {
            String[] value = {null};
            InvocationHandler handler =
                    (proxy, method, args) -> switch (method.getName()) {
                case "setString" -> {
                    value[0] = (String) args[1];
                    yield null;
                }
                case "executeUpdate" -> {
                    if (value[0].equals("+08:00") && timeZoneRestoreFailure != null) {
                        throw timeZoneRestoreFailure;
                    }
                    timeZone = value[0];
                    if (timeZone.equals("+00:00") && timeZoneSetupFailure != null) {
                        throw timeZoneSetupFailure;
                    }
                    yield 0;
                }
                case "executeQuery" -> {
                    assertThat(sql).isEqualTo("SELECT @@session.time_zone");
                    InvocationHandler rows =
                            (row, getter, params) -> switch (getter.getName()) {
                        case "next" -> true;
                        case "getString" -> timeZone;
                        case "close" -> null;
                        default -> throw new UnsupportedOperationException(getter.getName());
                    };
                    yield Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[] {ResultSet.class},
                            rows);
                }
                case "close" -> null;
                default -> throw new UnsupportedOperationException(method.getName());
            };
            return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                    new Class<?>[] {PreparedStatement.class}, handler);
        }
    }

    private static class SingleConnectionDataSource implements DataSource {
        private final Connection connection;

        private SingleConnectionDataSource(Connection connection) {
            this.connection = connection;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return connection;
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {}

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public void setLoginTimeout(int seconds) {}

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }

    private static final class FailingDataSource extends SingleConnectionDataSource {
        private final SQLException failure;

        private FailingDataSource(SQLException failure) {
            super(null);
            this.failure = failure;
        }

        @Override
        public Connection getConnection() throws SQLException {
            throw failure;
        }
    }
}
