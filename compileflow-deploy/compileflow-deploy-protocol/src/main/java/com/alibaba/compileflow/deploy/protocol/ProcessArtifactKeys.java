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
package com.alibaba.compileflow.deploy.protocol;

import com.alibaba.compileflow.engine.ProcessRef;

/**
 * Derives bounded, non-reversible transport keys for immutable process artifacts.
 *
 * @author yusu
 */
public final class ProcessArtifactKeys {
    private static final String DEFAULT_PREFIX = "compileflow.process.";

    private ProcessArtifactKeys() {
    }

    /**
     * Validates and normalizes an artifact key prefix.
     *
     * @param keyPrefix configured prefix, or blank to use the protocol default
     * @return portable validated prefix
     */
    public static String requirePrefix(String keyPrefix) {
        return ProtocolKeyCodec.keyPrefix(keyPrefix, DEFAULT_PREFIX);
    }

    /**
     * Derives the transport key for an immutable process version.
     *
     * @param keyPrefix configured key prefix, or {@code null} to use the protocol default
     * @param namespace process namespace
     * @param code      process code
     * @param version   immutable process version
     * @return bounded key containing a SHA-256 digest of the canonical identity tuple
     */
    public static String versioned(String keyPrefix, String namespace, String code, String version) {
        String prefix = requirePrefix(keyPrefix);
        ProcessRef.Version ref = ProcessRef.version(namespace, code, version);
        return prefix + "version." + ProtocolKeyCodec.digestIdentity(ref.namespace(), ref.code(), ref.version());
    }
}
