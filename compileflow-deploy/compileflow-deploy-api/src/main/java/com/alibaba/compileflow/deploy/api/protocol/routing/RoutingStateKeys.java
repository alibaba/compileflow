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
package com.alibaba.compileflow.deploy.api.protocol.routing;

import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessText;
import com.alibaba.compileflow.deploy.api.protocol.ProtocolKeyCodec;

/**
 * Derives and validates bounded, non-reversible transport keys for alias-routing state.
 *
 * @author yusu
 */
public final class RoutingStateKeys {
    /**
     * Default transport namespace for alias-routing state.
     */
    public static final String DEFAULT_PREFIX = "compileflow.deployment.";

    private RoutingStateKeys() {
    }

    /**
     * Derives the transport key for one published Alias.
     *
     * @param keyPrefix configured key prefix, or {@code null} to use {@link #DEFAULT_PREFIX}
     * @param namespace process namespace
     * @param code      process code
     * @param alias     explicit published Alias
     * @return bounded key containing a SHA-256 digest of the canonical identity tuple
     */
    public static String aliasState(String keyPrefix, String namespace, String code, String alias) {
        String prefix = ProtocolKeyCodec.keyPrefix(keyPrefix, DEFAULT_PREFIX);
        ProcessRef.Alias ref = ProcessRef.alias(namespace, code, alias);
        return prefix + aliasStateSuffix(ref.namespace(), ref.code(), ref.alias());
    }

    /**
     * Tests whether a transport key's identity suffix matches one alias.
     *
     * <p>The configured prefix is intentionally ignored because subscribers already own the exact
     * subscribed key and only need to bind payload identity to its non-reversible suffix.
     *
     * @param key       transport key to inspect
     * @param namespace process namespace
     * @param code      process code
     * @param alias     published Alias
     * @return {@code true} when the key suffix matches the canonical identity tuple
     */
    public static boolean matchesAliasState(String key, String namespace, String code, String alias) {
        if (key == null) {
            return false;
        }
        try {
            ProcessText.requireUnicode(key, "key");
        } catch (IllegalArgumentException failure) {
            return false;
        }
        if (key.isBlank() || !key.equals(ProcessText.strip(key))) {
            return false;
        }
        ProcessRef.Alias ref = ProcessRef.alias(namespace, code, alias);
        return key.endsWith(aliasStateSuffix(ref.namespace(), ref.code(), ref.alias()));
    }

    /**
     * Tests whether a value is a canonical alias-state transport key.
     *
     * <p>The prefix may differ between deployments, but it must satisfy the portable protocol
     * prefix constraints. The identity suffix is always a lowercase SHA-256 digest.
     *
     * @param key transport key to validate
     * @return {@code true} when the complete key has canonical alias-state structure
     */
    public static boolean isAliasStateKey(String key) {
        if (key == null) {
            return false;
        }
        int marker = key.lastIndexOf("alias.");
        if (marker <= 0) {
            return false;
        }
        String prefix = key.substring(0, marker);
        String digest = key.substring(marker + "alias.".length());
        try {
            ProtocolKeyCodec.keyPrefix(prefix, DEFAULT_PREFIX);
        } catch (IllegalArgumentException failure) {
            return false;
        }
        return digest.matches("[0-9a-f]{64}");
    }

    private static String aliasStateSuffix(String namespace, String code, String alias) {
        return "alias." + ProtocolKeyCodec.digestIdentity(namespace, code, alias);
    }
}
