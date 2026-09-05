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
package com.alibaba.compileflow.durable.runtime.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DurableEnvelopeTest {
    @Test
    void wrapsPayloadWithTheVersionedEngineHeader() {
        byte[] payload = "{\"value\":1}".getBytes(StandardCharsets.UTF_8);

        DurableStore.Envelope written = new DurableStore.Envelope(payload);
        DurableStore.Envelope restored = DurableStore.Envelope.fromStoredBytes(written.bytes());

        assertThat(written.bytes()).startsWith((byte) 'C', (byte) 'F', (byte) 'D', (byte) 1);
        assertThat(restored.formatVersion()).isEqualTo(1);
        assertThat(restored.payload()).containsExactly(payload);
        assertThat(restored).isEqualTo(written);
        assertThat(restored.toString()).doesNotContain("value");
    }

    @Test
    void failsClosedForRawOrUnknownPersistedFormats() {
        assertThatThrownBy(() -> DurableStore.Envelope.fromStoredBytes("{}".getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("header");
        assertThatThrownBy(() -> DurableStore.Envelope.fromStoredBytes(new byte[] {'C', 'F', 'D', 2, '{', '}'}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not supported");
    }
}
