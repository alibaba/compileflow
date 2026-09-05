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
package com.alibaba.compileflow.engine.core.routing;

import com.alibaba.compileflow.engine.ProcessAliasTarget;
import com.alibaba.compileflow.engine.ProcessRef;
import java.util.Objects;

/**
 * Immutable result of admitting one Alias to an authorized target.
 *
 * <p>ProcessEngine execution obtains this value from {@link AliasAdmission}. A trusted
 * admission adapter may pin the selection for a queued invocation so execution keeps the admitted
 * version and routing attribution instead of evaluating a newer Alias revision. Durable Process
 * Runs do not persist this value; they bind directly to the selected immutable Process Version.
 *
 * @author yusu
 */
public record AliasSelection(ProcessRef.Version version, ProcessAliasTarget target, long aliasRevision) {
    public AliasSelection {
        version = Objects.requireNonNull(version, "version");
        target = Objects.requireNonNull(target, "target");
        if (aliasRevision <= 0L) {
            throw new IllegalArgumentException("aliasRevision must be greater than 0");
        }
    }
}
