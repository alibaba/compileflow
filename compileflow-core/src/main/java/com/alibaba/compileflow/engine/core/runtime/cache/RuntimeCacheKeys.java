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
package com.alibaba.compileflow.engine.core.runtime.cache;

import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.ProcessRef;

/**
 * Helpers for building process runtime cache keys.
 *
 * @author yusu
 */
public final class RuntimeCacheKeys {
    private static final String SEPARATOR = "#";

    private RuntimeCacheKeys() {
    }

    public static String forProcess(String namespace, String code, String version) {
        if (version == null) {
            return ProcessIdentifiers.requireNamespace(namespace) + SEPARATOR + ProcessIdentifiers.requireCode(code);
        }
        ProcessRef.Version versionRef = ProcessRef.version(namespace, code, version);
        return versionRef.namespace() + SEPARATOR + versionRef.code() + SEPARATOR + versionRef.version();
    }
}
