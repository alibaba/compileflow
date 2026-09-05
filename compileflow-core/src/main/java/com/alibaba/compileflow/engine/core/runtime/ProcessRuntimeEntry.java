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
package com.alibaba.compileflow.engine.core.runtime;

import java.util.Objects;

/**
 * Exact process runtime paired with the local identity used to build it.
 *
 * @author yusu
 */
public final class ProcessRuntimeEntry {
    private final ProcessRuntime runtime;
    private final ProcessRuntimeIdentity runtimeIdentity;

    public ProcessRuntimeEntry(ProcessRuntime runtime, ProcessRuntimeIdentity runtimeIdentity) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.runtimeIdentity = Objects.requireNonNull(runtimeIdentity, "runtimeIdentity");
    }

    public ProcessRuntime getRuntime() {
        return runtime;
    }

    public String getDigest() {
        return runtimeIdentity.getDigest();
    }

    public ProcessRuntimeIdentity getRuntimeIdentity() {
        return runtimeIdentity;
    }

    public boolean matches(ProcessRuntimeIdentity requestedIdentity) {
        return runtimeIdentity.equals(requestedIdentity);
    }
}
