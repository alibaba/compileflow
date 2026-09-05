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
package com.alibaba.compileflow.engine.core.runtime.resolution;

import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeEntry;
import com.alibaba.compileflow.engine.core.routing.AliasSelection;
import java.util.Objects;

/**
 * Exact runtime and routing decision admitted for one execution.
 *
 * @author yusu
 */
public record ProcessRuntimeResolution(ProcessRuntimeEntry entry, String version, AliasSelection aliasSelection) {
    public ProcessRuntimeResolution {
        entry = Objects.requireNonNull(entry, "entry");
    }
}
