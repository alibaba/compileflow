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
package com.alibaba.compileflow.deploy.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.deploy.api.command.AbortRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.CreateRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.PromoteRolloutCommand;
import com.alibaba.compileflow.deploy.api.command.UpdateCanaryWeightCommand;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.spi.store.RolloutCreateRequest;
import com.alibaba.compileflow.engine.ProcessRef;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class JdbcRolloutConcurrencyFailureTest {
    @ParameterizedTest
    @CsvSource({"MYSQL,40001,1213", "MYSQL,40001,0", "MYSQL,,1213", "POSTGRESQL,40001,0", "POSTGRESQL,40P01,0"})
    void transactionConflictsRemainTypedAfterRollback(JdbcDeployStore.Dialect dialect, String state, int vendorCode) {
        assertFailureForEveryMutation(dialect, state, vendorCode, DeploymentErrorCode.CONCURRENT_MODIFICATION);
    }

    @ParameterizedTest
    @CsvSource({"MYSQL,08S01,0", "MYSQL,HY000,1205", "MYSQL,23000,1452", "MYSQL,40P01,0", "POSTGRESQL,08006,0",
            "POSTGRESQL,23503,0", "POSTGRESQL,HY000,1213"})
    void otherStorageFailuresAreNotReportedAsConcurrency(JdbcDeployStore.Dialect dialect, String state, int vendorCode) {
        assertFailureForEveryMutation(dialect, state, vendorCode, DeploymentErrorCode.STORAGE_ERROR);
    }

    private static void assertFailureForEveryMutation(JdbcDeployStore.Dialect dialect, String state, int vendorCode,
            DeploymentErrorCode expected) {
        RolloutCreateRequest request = RolloutCreateRequest.deploy(CreateRolloutCommand.allAtOnce("request-1",
                ProcessRef.alias("sales", "order", "production"), ProcessRef.version("sales", "order", "v1"), 0L,
                "operator", null));
        List<Consumer<JdbcRolloutStore>> mutations = List.of(store -> store.create(request), store -> store.updateCanary(
                new UpdateCanaryWeightCommand("rollout-1", 1_000, 1L, "operator")), store -> store.promote(
                new PromoteRolloutCommand("rollout-1", 1L, "operator")), store -> store.abort(
                new AbortRolloutCommand("rollout-1", 1L, "operator", "test")));
        for (boolean wrapped : List.of(false, true)) {
            for (Consumer<JdbcRolloutStore> mutation : mutations) {
                SQLException sql = new SQLException("driver-private-detail", state, vendorCode);
                Throwable injected = wrapped
                        ? DeploymentException.of(DeploymentErrorCode.STORAGE_ERROR, "nested JDBC operation failed", sql)
                        : sql;
                TransactionProbe probe = new TransactionProbe(injected);
                JdbcRolloutStore store = new JdbcRolloutStore(probe.dataSource(), "compileflow", dialect);

                assertThatThrownBy(() -> mutation.accept(store)).isInstanceOfSatisfying(DeploymentException.class, failure -> {
                    assertThat(failure.getErrorCode()).isEqualTo(expected);
                    assertThat(failure.getMessage()).doesNotContain("driver-private-detail");
                    assertThat(failure.getCause()).isSameAs(
                            wrapped && expected == DeploymentErrorCode.STORAGE_ERROR ? sql : injected);
                });
                assertThat(probe.events).containsExactly("autoCommit=false", "rollback", "autoCommit=true", "close");
                assertThat(probe.connections).isOne();
            }
        }
    }

    private static final class TransactionProbe {
        private final Throwable failure;
        private final List<String> events = new ArrayList<>();
        private int connections;
        private int preparations;

        private TransactionProbe(Throwable failure) {
            this.failure = failure;
        }

        private DataSource dataSource() {
            InvocationHandler connectionHandler =
                    (proxy, method, arguments) -> switch (method.getName()) {
                case "getAutoCommit" -> true;
                case "setAutoCommit" -> {
                    events.add("autoCommit=" + arguments[0]);
                    yield null;
                }
                case "prepareStatement" -> {
                    if (++preparations > 1) {
                        throw new SQLException("recovery unavailable", "08006");
                    }
                    throw failure;
                }
                case "rollback" -> {
                    events.add("rollback");
                    yield null;
                }
                case "close" -> {
                    events.add("close");
                    yield null;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            };
            Connection connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class}, connectionHandler);
            InvocationHandler dataSourceHandler =
                    (proxy, method, arguments) -> {
                if (method.getName().equals("getConnection")) {
                    connections++;
                    return connection;
                }
                throw new UnsupportedOperationException(method.getName());
            };
            return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                    new Class<?>[] {DataSource.class}, dataSourceHandler);
        }
    }
}
