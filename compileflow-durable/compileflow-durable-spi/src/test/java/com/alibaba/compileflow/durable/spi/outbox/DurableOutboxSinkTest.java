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
package com.alibaba.compileflow.durable.spi.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import com.alibaba.compileflow.engine.ProcessRef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DurableOutboxSinkTest {
    @Test
    void outboundEventOwnsAnImmutableLogicalPayloadAndRedactsIt() {
        List<Object> attributes = new ArrayList<>(List.of("original"));
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("waitToken", "secret-capability");
        source.put("attributes", attributes);

        DurableOutboxSink.OutboundEvent event = new DurableOutboxSink.OutboundEvent(UUID.fromString(
                        "00000000-0000-0000-0000-000000000001"),
                new ProcessRunId("00000000-0000-0000-0000-000000000002"), "test", "approval",
                ProcessRef.version("test", "approval", "v1"), "WAIT_COMMITTED",
                UUID.fromString("00000000-0000-0000-0000-000000000003"), source);

        source.put("late", true);
        attributes.add("late");

        assertThat(event.payload()).isEqualTo(Map.of("waitToken", "secret-capability", "attributes", List.of("original")));
        assertThat(event.namespace()).isEqualTo("test");
        assertThatThrownBy(() -> event.payload().put("late", true)).isInstanceOf(UnsupportedOperationException.class);
        List<?> detachedAttributes = (List<?>) event.payload().get("attributes");
        assertThatThrownBy(detachedAttributes::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThat(event.toString().contains("secret-capability")).isFalse();
    }

    @Test
    void outboundEventRejectsAnUnknownEventType() {
        assertThatThrownBy(() -> new DurableOutboxSink.OutboundEvent(UUID.fromString(
                        "00000000-0000-0000-0000-000000000004"),
                new ProcessRunId("00000000-0000-0000-0000-000000000005"), "test", "approval", null, "UNKNOWN", null,
                Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("supported Durable Outbox event");
    }
}
