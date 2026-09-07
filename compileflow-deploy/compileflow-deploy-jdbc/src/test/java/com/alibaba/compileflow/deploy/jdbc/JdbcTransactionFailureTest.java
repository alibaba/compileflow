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
import com.alibaba.compileflow.deploy.api.command.CreateRolloutCommand;
import com.alibaba.compileflow.deploy.spi.store.RolloutCreateRequest;
import com.alibaba.compileflow.engine.ProcessRef;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcTransactionFailureTest {
    private static final AssertionError FATAL = new AssertionError("simulated fatal transaction failure");

    @Test
    void rolloutRollsBackFatalFailureBeforeRestoringAutoCommit() {
        TransactionProbe probe = new TransactionProbe();
        JdbcRolloutStore store =
                new JdbcRolloutStore(probe.dataSource(), "compileflow", JdbcDeployStore.Dialect.POSTGRESQL);
        RolloutCreateRequest request = RolloutCreateRequest.deploy(CreateRolloutCommand.allAtOnce("request-1",
                ProcessRef.alias("sales", "order", "production"), ProcessRef.version("sales", "order", "v1"), 0L,
                "operator", null));

        assertThatThrownBy(() -> store.create(request)).isSameAs(FATAL);

        assertThat(probe.events).containsExactly("autoCommit=false", "rollback", "autoCommit=true", "close");
    }

    @Test
    void outboxClaimRollsBackFatalFailureBeforeRestoringAutoCommit() {
        TransactionProbe probe = new TransactionProbe();
        JdbcRoutingOutboxStore store = new JdbcRoutingOutboxStore(probe.dataSource(), JdbcDeployStore.Dialect.MYSQL);

        assertThatThrownBy(() -> store.claimPending(1, "worker-1", 1_000L)).isSameAs(FATAL);

        assertThat(probe.events).containsExactly("autoCommit=false", "rollback", "autoCommit=true", "close");
    }

    private static final class TransactionProbe {
        private final List<String> events = new ArrayList<>();

        private DataSource dataSource() {
            InvocationHandler connectionHandler =
                    (proxy, method, arguments) -> switch (method.getName()) {
                case "getAutoCommit" -> true;
                case "setAutoCommit" -> {
                    events.add("autoCommit=" + arguments[0]);
                    yield null;
                }
                case "prepareStatement" -> throw FATAL;
                case "rollback" -> {
                    events.add("rollback");
                    yield null;
                }
                case "close" -> {
                    events.add("close");
                    yield null;
                }
                case "isClosed" -> false;
                case "unwrap" -> null;
                case "isWrapperFor" -> false;
                default -> throw new UnsupportedOperationException(method.getName());
            };
            Connection connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class}, connectionHandler);
            InvocationHandler dataSourceHandler =
                    (proxy, method, arguments) -> switch (method.getName()) {
                case "getConnection" -> connection;
                case "unwrap" -> null;
                case "isWrapperFor" -> false;
                default -> throw new UnsupportedOperationException(method.getName());
            };
            return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                    new Class<?>[] {DataSource.class}, dataSourceHandler);
        }
    }
}
