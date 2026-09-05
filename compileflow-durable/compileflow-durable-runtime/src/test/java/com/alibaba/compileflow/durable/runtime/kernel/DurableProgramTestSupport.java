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
package com.alibaba.compileflow.durable.runtime.kernel;

import com.alibaba.compileflow.durable.runtime.program.DurableExecutionContext;
import com.alibaba.compileflow.durable.runtime.program.DurableProgram;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Test-only adapters that keep assertions focused on Process behavior around one Machine Turn.
 *
 * @author yusu
 */
public final class DurableProgramTestSupport {
    private DurableProgramTestSupport() {
    }

    public static FrontierStepResult advanceStart(DurableProgram program, Map<String, ?> input,
            DurableExecutionContext context) throws Exception {
        Map<String, Object> state = new LinkedHashMap<>();
        input.forEach(state::put);
        return program.advance(ContinuationSnapshot.start(state), List.of(), TurnBudget.defaults(), context).outcome();
    }

    public static FrontierStepResult advanceAfter(DurableProgram program, SemanticCheckpoint checkpoint,
            Map<String, Object> state, BoundaryCompletion result, DurableExecutionContext context) throws Exception {
        return program
            .advance(new ContinuationSnapshot(checkpoint.resumePoint(), state, checkpoint.scopeFrames()),
                    List.of(resolved(result)), TurnBudget.defaults(), context)
            .outcome();
    }

    public static OccurrenceResult resolved(BoundaryCompletion completion) {
        return new OccurrenceResult(new OccurrenceKey(completion.kind(), UUID.randomUUID()), FrontierId.ROOT, completion);
    }
}
