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

import com.alibaba.compileflow.durable.api.validation.DurableIdentifiers;
import java.util.Objects;

/**
 * Identity-only lineage for one parallel collection iteration.
 *
 * @author yusu
 */
public record MultiInstanceBranchFrame(FrontierId parentFrontierId, String loopId, int index) implements BranchFrame {
    public MultiInstanceBranchFrame {
        parentFrontierId = Objects.requireNonNull(parentFrontierId, "parentFrontierId");
        loopId = requireText(loopId);
        if (index < 0) {
            throw new IllegalArgumentException("parallel iteration index must be non-negative");
        }
    }

    @Override
    public FrontierId frontierId() {
        return FrontierId.branch(parentFrontierId, loopId, "iteration:" + index);
    }

    private static String requireText(String value) {
        return DurableIdentifiers.requireIdentity(value, "loopId", 128);
    }
}
