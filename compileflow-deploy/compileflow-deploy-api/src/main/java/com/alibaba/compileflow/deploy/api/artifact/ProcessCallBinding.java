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
package com.alibaba.compileflow.deploy.api.artifact;

import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.ProcessRef;
import java.util.Objects;

/**
 * One source-derived exact Process call edge in an immutable published artifact.
 *
 * @param callSiteId source-stable call-site identity within the caller
 * @param target exact immutable Version selected for the call site
 *
 * @author yusu
 */
public record ProcessCallBinding(String callSiteId, ProcessRef.Version target) {
    /**
     * Validates one exact call-site binding.
     *
     * @param callSiteId source-stable call-site identity within the caller
     * @param target exact immutable Version selected for the call site
     */
    public ProcessCallBinding {
        callSiteId = ProcessIdentifiers.requireNodeId(callSiteId);
        target = Objects.requireNonNull(target, "target");
    }

    /**
     * Returns the called Process code from the exact target.
     *
     * @return called Process code
     */
    public String code() {
        return target.code();
    }
}
