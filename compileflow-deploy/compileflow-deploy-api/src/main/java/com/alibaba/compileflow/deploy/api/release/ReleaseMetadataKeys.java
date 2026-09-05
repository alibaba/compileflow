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
package com.alibaba.compileflow.deploy.api.release;

/**
 * Stable metadata keys understood by the built-in publication pipeline.
 *
 * <p>The {@code compileflow.} namespace is reserved for keys defined by this class. Applications
 * may add bounded metadata entries under their own namespace.
 *
 * @author yusu
 */
public final class ReleaseMetadataKeys {
    /**
     * Namespace reserved for metadata keys defined by CompileFlow.
     */
    public static final String RESERVED_PREFIX = "compileflow.";
    /**
     * Human-readable release notes associated with the immutable version.
     */
    public static final String CHANGELOG = RESERVED_PREFIX + "changelog";

    private ReleaseMetadataKeys() {
    }

    static boolean isSupportedReservedKey(String key) {
        return CHANGELOG.equals(key);
    }
}
