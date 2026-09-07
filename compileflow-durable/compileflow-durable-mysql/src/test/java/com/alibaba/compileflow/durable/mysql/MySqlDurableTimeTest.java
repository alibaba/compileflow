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
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import com.mysql.cj.conf.DefaultPropertySet;
import com.mysql.cj.protocol.InternalTimestamp;
import com.mysql.cj.result.SqlTimestampValueFactory;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Connector/J conversion and JDBC-boundary regressions, not a real database contract.
 */
class MySqlDurableTimeTest {
    private static final Instant INSTANT = Instant.parse("2026-11-01T01:30:00.123Z");

    @ParameterizedTest
    @ValueSource(strings = {"Asia/Shanghai", "America/New_York", "UTC"})
    void readsDatetimeAsUtcRegardlessOfDriverTimeZone(String zone) throws Exception {
        ResultSet rows = (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[] {ResultSet.class}, (
                                                                                                                                        proxy,
                                                                                                                                        method,
                                                                                                                                        args
                                                                                                                                ) -> {
            if (!method.getName().equals("getTimestamp")) {
                throw new UnsupportedOperationException(method.getName());
            }
            Calendar calendar = args.length == 2 ? (Calendar) args[1] : null;
            var factory = new SqlTimestampValueFactory(new DefaultPropertySet(), calendar, TimeZone.getTimeZone(zone),
                    TimeZone.getTimeZone(zone));
            return factory.localCreateFromDatetime(InternalTimestamp.from(LocalDateTime.parse("2026-11-01T01:30:00.123")));
        });
        assertThat(invoke("instant", new Class<?>[] {ResultSet.class, String.class}, rows, "created_at")).isEqualTo(
                INSTANT);
        assertThat(invoke("nullableInstant", new Class<?>[] {ResultSet.class, String.class}, rows, "completed_at"))
            .isEqualTo(INSTANT);
    }

    @Test
    void bindsQueryCursorsWithAnExplicitUtcCalendar() throws Exception {
        invoke("bindParameters", new Class<?>[] {PreparedStatement.class, List.class}, timestampStatement(),
                List.of(INSTANT));
    }

    @Test
    void bindsAbsoluteTimerWithAnExplicitUtcCalendar() throws Exception {
        Connection connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (
                                                                                                                                                  proxy,
                                                                                                                                                  method,
                                                                                                                                                  args
                                                                                                                                          ) -> {
            if (method.getName().equals("prepareStatement")) {
                return timestampStatement();
            }
            throw new UnsupportedOperationException(method.getName());
        });
        var timer = new DurableStore.TimerCommit(new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.TIMER,
                        UUID.randomUUID()), 1, UUID.randomUUID(), 0, "frontier", "timer", null, INSTANT);
        invoke("insertTimerOccurrence", new Class<?>[] {Connection.class, UUID.class, DurableStore.TimerCommit.class},
                connection, UUID.randomUUID(), timer);
    }

    private static PreparedStatement timestampStatement() {
        return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[] {PreparedStatement.class}, (
                                                                                                                                                      proxy,
                                                                                                                                                      method,
                                                                                                                                                      args
                                                                                                                                              ) -> {
            if (method.getName().equals("setTimestamp")) {
                assertThat(args).hasSize(3);
                assertThat(((Calendar) args[2]).getTimeZone().getID()).isEqualTo("UTC");
                assertThat(((Timestamp) args[1]).toInstant()).isEqualTo(INSTANT);
            }
            if (method.getName().equals("executeUpdate")) {
                return 1;
            }
            if (method.getName().equals("executeQuery")) {
                boolean[] next = {true};
                return Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[] {ResultSet.class}, (rows,
                                                                                                                          getter,
                                                                                                                          params
                                                                                                                  ) -> switch (getter.getName()) {
                    case "next" -> {
                        boolean value = next[0];
                        next[0] = false;
                        yield value;
                    }
                    case "getLong" -> 0L;
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(getter.getName());
                });
            }
            return null;
        });
    }

    private static Object invoke(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = MySqlDurableStore.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(null, args);
    }
}
