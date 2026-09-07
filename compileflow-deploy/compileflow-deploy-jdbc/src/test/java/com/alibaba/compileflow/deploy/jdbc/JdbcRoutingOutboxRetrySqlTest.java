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
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcRoutingOutboxRetrySqlTest {
    @Test
    void retryDecisionsReadTheOriginalCountUnderLeftToRightAssignments() {
        AtomicReference<String> updateSql = new AtomicReference<>();
        Map<Integer, Object> parameters = new HashMap<>();
        ResultSet clock = proxy(ResultSet.class, (object, method, arguments) -> switch (method.getName()) {
            case "next" -> true;
            case "getTimestamp" -> new Timestamp(1_000L);
            case "close" -> null;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        PreparedStatement statement = proxy(PreparedStatement.class, (object, method, arguments) -> {
            if (method.getName().startsWith("set")) {
                parameters.put((Integer) arguments[0], arguments[1]);
                return null;
            }
            return switch (method.getName()) {
                case "executeQuery" -> clock;
                case "executeUpdate" -> 1;
                case "close" -> null;
                default -> throw new UnsupportedOperationException(method.getName());
            };
        });
        Connection connection = proxy(Connection.class, (object, method, arguments) -> switch (method.getName()) {
            case "prepareStatement" -> {
                if (((String) arguments[0]).startsWith("UPDATE")) {
                    updateSql.set((String) arguments[0]);
                }
                yield statement;
            }
            case "close" -> null;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        DataSource dataSource = proxy(DataSource.class, (object, method, arguments) -> {
            if (method.getName().equals("getConnection")) {
                return connection;
            }
            throw new UnsupportedOperationException(method.getName());
        });
        JdbcRoutingOutboxStore store = new JdbcRoutingOutboxStore(dataSource, JdbcDeployStore.Dialect.MYSQL);

        assertThat(store.markFailed(7L, "lease", "temporary", 2, 50L)).isEqualTo(1);

        String sql = updateSql.get();
        // MySQL single-table UPDATE reads assignments already made to its left.
        int increment = sql.indexOf("attempt_count = attempt_count + 1");
        assertThat(increment).isGreaterThan(sql.indexOf("next_attempt_at = CASE"));
        assertThat(increment).isGreaterThan(sql.indexOf("status = CASE"));
        assertThat(sql).contains("WHERE id = ? AND status = 'PROCESSING' AND lease_token = ? AND lease_until >= ?");
        assertThat(parameters)
            .containsEntry(2, 2)
            .containsEntry(3, 1_050L)
            .containsEntry(4, 2)
            .containsEntry(6, 7L)
            .containsEntry(7, "lease")
            .containsEntry(8, 1_000L);
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
