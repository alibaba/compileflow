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

import com.alibaba.compileflow.durable.runtime.kernel.ContinuationSnapshot;
import com.alibaba.compileflow.durable.runtime.kernel.MachineTurnResult;
import com.alibaba.compileflow.durable.runtime.kernel.OccurrenceResult;
import com.alibaba.compileflow.durable.runtime.kernel.TurnBudget;
import java.util.List;

/**
 * Contract implemented by one compiled durable Process program.
 *
 * @author yusu
 */
public interface DurableProgram {
    /**
     * Advances one committed continuation through one bounded, deterministic Machine Turn.
     */
    MachineTurnResult advance(ContinuationSnapshot continuation, List<OccurrenceResult> availableResults,
            TurnBudget budget, DurableExecutionContext context) throws Exception;
}
