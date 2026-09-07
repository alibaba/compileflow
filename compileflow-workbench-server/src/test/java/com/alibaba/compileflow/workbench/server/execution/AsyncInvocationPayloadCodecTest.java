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
package com.alibaba.compileflow.workbench.server.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import java.util.Map;
import java.math.BigInteger;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class AsyncInvocationPayloadCodecTest {
    private final AsyncInvocationPayloadCodec codec = new AsyncInvocationPayloadCodec();

    @Test
    void roundTripsOnlyJsonData() {
        Map<String, Object> value = Map.of("name", "order", "count", 2, "items", List.of("a", "b"));

        assertThat(codec.readMap("paramsJson", codec.write(value))).isEqualTo(value);
    }

    @Test
    void rejectsAmbiguousOrTrailingJson() {
        assertThatThrownBy(() -> codec.readMap("paramsJson", "{\"value\":1,\"value\":2}"))
            .isInstanceOf(AsyncInvocationPayloadException.class);
        assertThatThrownBy(() -> codec.readMap("paramsJson", "{\"value\":1} []"))
            .isInstanceOf(AsyncInvocationPayloadException.class);
    }

    @Test
    void rejectsOversizedPayloadsBeforePersistence() {
        String oversized = "x".repeat(4 * 1024 * 1024 + 1);

        assertThatThrownBy(() -> codec.write(Map.of("value", oversized)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("character limit");
    }

    @Test
    void rejectsPayloadsThatExceedItsOwnReadConstraintsBeforePersistence() {
        Map<String, Object> nested = Map.of("leaf", true);
        for (int depth = 0; depth < 64; depth++) {
            nested = Map.of("child", nested);
        }
        Map<String, Object> tooDeep = nested;

        assertThatThrownBy(() -> codec.write(tooDeep)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.write(Map.of("x".repeat(513), true))).isInstanceOf(
                IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.write(Map.of("items", Collections.nCopies(200_000, 0))))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.write(Map.of("number", BigInteger.TEN.pow(1000))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void routeRevisionNeverTruncatesFractionalOrOverflowingJsonNumbers() {
        for (String revision : List.of("1.5", "1e40", "18446744073709551617", "0", "-1")) {
            assertThat(codec.readRouting("routingJson", "{\"routeRevision\":" + revision + "}").routeRevision())
                .as("revision %s", revision)
                .isNull();
        }
        assertThat(codec.readRouting("routingJson", "{\"routeRevision\":9223372036854775807}").routeRevision())
            .isEqualTo(Long.MAX_VALUE);
    }
}
