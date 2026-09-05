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
package com.alibaba.compileflow.deploy.runtime.install;

import com.alibaba.compileflow.engine.ProcessRef;

/**
 * Canonical owner identifiers for runtime retention.
 *
 * @author yusu
 */
public final class RuntimeOwnerIds {
    private RuntimeOwnerIds() {
    }

    public static String alias(String namespace, String code, String alias) {
        ProcessRef.Alias ref = ProcessRef.alias(namespace, code, alias);
        return "alias:" + ref.namespace() + "/" + ref.code() + "@" + ref.alias();
    }

    /**
     * Returns a process-local owner for one execution-scoped installation lease.
     *
     * @param sequence positive installer-local sequence
     * @return canonical owner identifier
     */
    public static String installationLease(long sequence) {
        if (sequence <= 0L) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        return "execution:" + sequence;
    }
}
