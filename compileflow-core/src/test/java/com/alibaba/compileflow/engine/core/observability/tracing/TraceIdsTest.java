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
package com.alibaba.compileflow.engine.core.observability.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ProcessIdentifiers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class TraceIdsTest {
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void usesTheConfiguredProviderWithoutNormalizingItsIdentity() {
        assertThat(TraceIds.current(() -> "upstream-trace")).isEqualTo("upstream-trace");
        assertThat(TraceIds.current(() -> " upstream-trace ")).matches("[0-9a-f]{32}");
    }

    @Test
    void generatesALocalIdentifierWhenTheProviderCannotSupplyOne() {
        assertThat(TraceIds.current(() -> "  ")).matches("[0-9a-f]{32}");
        assertThat(TraceIds.current(() -> "trace\ud800")).matches("[0-9a-f]{32}");
        assertThat(TraceIds.current(() -> {
            throw new IllegalStateException("provider unavailable");
        })).matches("[0-9a-f]{32}");
    }

    @Test
    void acceptsTheMaximumProviderIdentifierWithoutTruncation() {
        String maximum = "t".repeat(ProcessIdentifiers.MAX_TRACE_ID_LENGTH);

        assertThat(TraceIds.current(() -> maximum)).isEqualTo(maximum);
    }

    @Test
    void rejectsOversizedProviderAndMdcIdentifiersBeforeExecution() {
        String oversized = "t".repeat(ProcessIdentifiers.MAX_TRACE_ID_LENGTH + 1);
        MDC.put("traceId", oversized);

        assertThat(TraceIds.current(() -> oversized)).matches("[0-9a-f]{32}").isNotEqualTo(oversized);
    }

    @Test
    void fallsBackFromAnOversizedProviderToAValidMdcIdentifier() {
        MDC.put("traceId", "mdc-trace");

        assertThat(TraceIds.current(() -> "t".repeat(ProcessIdentifiers.MAX_TRACE_ID_LENGTH + 1))).isEqualTo(
                "mdc-trace");
    }
}
