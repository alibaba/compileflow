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
package com.alibaba.compileflow.engine.spi.routing;

import com.alibaba.compileflow.engine.ProcessAliasTarget;
import com.alibaba.compileflow.engine.ProcessIdentifiers;
import java.util.Optional;

/**
 * Applies an explicit enterprise targeting override before standard Alias percentage routing.
 *
 * <p>A policy may force only the stable or candidate target authorized by the supplied context.
 * An empty result delegates to CompileFlow's fixed deterministic percentage selector. Policy
 * implementations must be thread-safe, deterministic, bounded, and free of blocking remote I/O
 * or mutable ambient state. A thrown exception or null result selects stable with explicit error
 * attribution; it never changes the fixed percentage algorithm. The application or
 * dependency-injection container owns the policy lifecycle.
 *
 * @author yusu
 */
public interface ProcessAliasTargetingPolicy {
    int MAX_NAME_CHARACTERS = 256;

    /**
     * Validates a stable lowercase kebab-case policy name.
     *
     * @param name policy name
     * @return validated name unchanged
     */
    static String requireCanonicalName(String name) {
        String value = ProcessIdentifiers.requireExactIdentity(name, "Alias targeting policy name", MAX_NAME_CHARACTERS);
        if (!value.matches("[a-z][a-z0-9]*(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException("Alias targeting policy name must use lowercase kebab-case: " + value);
        }
        return value;
    }

    /**
     * Returns the stable semantic name referenced by Alias routes.
     *
     * @return lowercase kebab-case policy name
     */
    String name();

    /**
     * Returns an explicit target override, or empty to use standard percentage routing.
     *
     * @param context immutable route targeting configuration and invocation inputs
     * @return stable/candidate override or empty
     */
    Optional<ProcessAliasTarget> target(ProcessAliasTargetingContext context);
}
