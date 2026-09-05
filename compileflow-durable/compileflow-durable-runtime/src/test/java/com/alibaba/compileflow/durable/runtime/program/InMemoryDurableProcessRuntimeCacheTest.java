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
package com.alibaba.compileflow.durable.runtime.program;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.durable.runtime.kernel.ContinuationSnapshot;
import com.alibaba.compileflow.durable.runtime.codec.DurableValueSerializer;
import com.alibaba.compileflow.durable.runtime.kernel.FrontierStepResult;
import com.alibaba.compileflow.durable.runtime.kernel.MachineTurnResult;
import com.alibaba.compileflow.durable.runtime.kernel.OccurrenceResult;
import com.alibaba.compileflow.durable.runtime.kernel.TurnBudget;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.tbbpm.parser.TbbpmXmlParser;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryDurableProcessRuntimeCacheTest {
    @Test
    void evictsLeastRecentlyUsedDisposableProgramAtTheBound() {
        InMemoryDurableProcessRuntimeCache cache = new InMemoryDurableProcessRuntimeCache(2);
        DurableProcessRuntime first = program("cache.first");
        DurableProcessRuntime second = program("cache.second");
        DurableProcessRuntime third = program("cache.third");

        cache.put(first);
        cache.put(second);
        assertThat(cache.get(first.processId())).contains(first);
        cache.put(third);

        assertThat(cache.get(first.processId())).contains(first);
        assertThat(cache.get(second.processId())).isEmpty();
        assertThat(cache.get(third.processId())).contains(third);
        assertThat(cache.snapshot()).hasSize(2);
    }

    @Test
    void rejectsAnUnboundedOrUnreasonablyLargeCache() {
        assertThatThrownBy(() -> new InMemoryDurableProcessRuntimeCache(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InMemoryDurableProcessRuntimeCache(10_001)).isInstanceOf(
                IllegalArgumentException.class);
    }

    private DurableProcessRuntime program(String code) {
        String xml = """
            <bpm code="%s">
              <start id="start" g="0,0,32,32"><transition to="end"/></start>
              <end id="end" g="80,0,32,32"/>
            </bpm>
            """
            .formatted(code);
        var model = TbbpmXmlParser.getInstance().parse(FlowSource.of(code, xml.getBytes(StandardCharsets.UTF_8)));
        var machinePlan = DurableCompilerTestSupport.lower(model);
        var program = new CompletingProgram();
        return new DurableProcessRuntime(UUID.nameUUIDFromBytes(code.getBytes(StandardCharsets.UTF_8)), code,
                ProcessModelType.TBBPM, "0".repeat(64), machinePlan, program, new DurableValueSerializer(machinePlan),
                Map.of());
    }

    private static final class CompletingProgram implements DurableProgram {
        @Override
        public MachineTurnResult advance(ContinuationSnapshot continuation, List<OccurrenceResult> availableResults,
                TurnBudget budget, DurableExecutionContext context) {
            return new MachineTurnResult(new FrontierStepResult.Completed(continuation.variables()), List.of());
        }
    }
}
