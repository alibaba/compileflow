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

/**
 * One active parallel collection iteration; its parent Process state or scope owns the frozen collection.
 *
 * @author yusu
 */
public record ParallelForEachFrame(String loopId, int position, Object currentValue) implements ScopeFrame {
    public ParallelForEachFrame {
        loopId = requireText(loopId);
        if (position < 0) {
            throw new IllegalArgumentException("parallel foreach position must be non-negative");
        }
        currentValue = DurableValueSnapshots.detachedValue(currentValue);
    }

    @Override
    public Object currentValue() {
        return DurableValueSnapshots.detachedValue(currentValue);
    }

    private static String requireText(String value) {
        return DurableIdentifiers.requireIdentity(value, "loopId", 128);
    }
}
