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
package com.alibaba.compileflow.engine.spi.script;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import java.util.Map;

/**
 * Executes explicit script-action source for one semantic language used by process definitions.
 * <p>
 * Executors are keyed by the exact canonical value returned by {@link #name()}. Executor classes,
 * libraries and compiled programs are runtime capabilities and never become process
 * identity. The current deployment is authoritative for resolving and interpreting
 * a language name; provider or application compatibility is outside the engine's
 * persisted contract.
 * <p>
 * Implementations must be thread-safe because one executor may serve concurrent Process
 * invocations and logical paths. If a compiled program is shared, the provider must make its
 * evaluation concurrency-safe or provide invocation isolation. The application or
 * dependency-injection container owns the executor lifecycle. Script programs are disposable
 * runtime artifacts.
 *
 * @author yusu
 */
public interface ScriptExecutor {
    /**
     * Validates a canonical language name for registration and lookup.
     *
     * @param name non-blank script language name
     * @return the unchanged lowercase kebab-case language key
     */
    static String requireCanonicalName(String name) {
        if (name == null || name.isBlank()) {
            throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_001,
                    "Script executor name must not be blank");
        }
        if (!name.equals(name.trim())) {
            throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_001,
                    "Script executor name must not contain surrounding whitespace");
        }
        if (name.length() > 256) {
            throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_001,
                    "Script executor name must not exceed 256 characters");
        }
        if (name.chars().anyMatch(Character::isISOControl)) {
            throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_001,
                    "Script executor name must not contain control characters");
        }
        if (!name.matches("[a-z][a-z0-9]*(?:-[a-z0-9]+)*")) {
            throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_001,
                    "Script executor name must use lowercase kebab-case: " + name);
        }
        return name;
    }

    /**
     * Returns the stable lowercase kebab-case language name.
     *
     * @return script language name
     */
    String name();

    /**
     * Validates one fully described script program without executing it.
     * Implementations must reject invalid syntax and forbidden language dependencies.
     * Validation must not observe application or external state.
     *
     * The specification language must equal {@link #name()}.
     *
     * @param spec definition-owned source and declared action signature
     * @throws ScriptException when the specification is invalid for this language provider
     */
    void validate(ScriptProgramSpec spec);

    /**
     * Compiles one fully described script program for evaluation.
     * <p>
     * The returned value is runtime-local and disposable. It must not be persisted,
     * hashed into process identity, or required for recovery.
     *
     * The specification language must equal {@link #name()}.
     *
     * @param spec definition-owned source and declared action signature
     * @return executor-owned script program
     * @throws ScriptException when compilation fails
     */
    ScriptProgram compile(ScriptProgramSpec spec);

    /**
     * Evaluates a script program against borrowed, read-only variables.
     * Implementations must not mutate the supplied context or any object reachable from it. Whether observation is
     * allowed is decided by the owning Action's execution classification: replayable
     * actions require a replay-safe provider/source deployment contract, while effect
     * actions execute behind a Durable boundary.
     *
     * @param script  program returned by this executor
     * @param context variables visible to the script
     * @return evaluated result
     * @throws ScriptException when evaluation cannot produce a valid result
     */
    Object evaluate(ScriptProgram script, Map<String, Object> context);
}
