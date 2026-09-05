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
package com.alibaba.compileflow.durable.spi.wait;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DurableWaitDescriptionProviderTest {
    @Test
    void defaultsProvideNoApplicationWaitAttributes() throws Exception {
        DurableWaitDescriptionProvider context = DurableWaitDescriptionProvider.defaults();
        DurableWaitDescriptionContext wait =
                new DurableWaitDescriptionContext("order", "a".repeat(64), "approval", Map.of(), Map.of());
        assertThat(context.describeWait(wait)).isEqualTo(Map.of());
    }

    @Test
    void contextValidatesIdentityAndDetachesSuppliedMaps() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("orderId", "42");
        DurableWaitDescriptionContext context =
                new DurableWaitDescriptionContext("order", "a".repeat(64), "approval", state, Map.of("item", "line-1"));

        state.clear();

        assertThat(context.state()).containsEntry("orderId", "42");
        assertThatThrownBy(() -> context.state().put("other", "value")).isInstanceOf(
                UnsupportedOperationException.class);
        assertThatThrownBy(() -> new DurableWaitDescriptionContext("order", "not-a-digest", "approval", Map.of(),
                Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
