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
package com.alibaba.compileflow.engine.core.runtime.script;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.spi.script.ScriptProgram;
import com.alibaba.compileflow.engine.spi.script.ScriptException;
import com.alibaba.compileflow.engine.spi.script.ScriptExecutor;
import com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable engine-scoped lookup table for script execution.
 *
 * @author yusu
 */
public final class ScriptExecutorRegistry implements AutoCloseable {
    private final Map<String, ScriptExecutor> scriptExecutorMap;
    private final List<QlExpressScriptExecutor> ownedExecutors;

    private ScriptExecutorRegistry(Map<String, ScriptExecutor> scriptExecutors,
            List<QlExpressScriptExecutor> ownedExecutors) {
        this.scriptExecutorMap = Collections.unmodifiableMap(new LinkedHashMap<>(scriptExecutors));
        this.ownedExecutors = List.copyOf(ownedExecutors);
    }

    /**
     * Creates an engine-scoped registry containing the built-in script executors.
     *
     * @param config immutable engine configuration
     * @return registry with the configured core-provided executor
     */
    public static ScriptExecutorRegistry builtIns(ProcessEngineConfig config) {
        return assemble(config, Map.of());
    }

    /**
     * Creates a registry containing built-ins and configured language providers.
     * Semantic language names must be unique, and built-in providers cannot be replaced.
     *
     * @param config    immutable engine configuration
     * @param executors user-provided executors from the engine configuration
     * @return script executor registry
     */
    public static ScriptExecutorRegistry configured(ProcessEngineConfig config,
            Collection<? extends ScriptExecutor> executors) {
        return assemble(config, index(executors));
    }

    /**
     * Creates a language registry from the supplied executors only.
     *
     * @param executors script executors in startup registration order
     * @return script executor registry
     */
    public static ScriptExecutorRegistry from(Collection<? extends ScriptExecutor> executors) {
        return new ScriptExecutorRegistry(index(executors), List.of());
    }

    private static Map<String, ScriptExecutor> index(Collection<? extends ScriptExecutor> executors) {
        Map<String, ScriptExecutor> resolved = new LinkedHashMap<>();
        for (ScriptExecutor executor : Objects.requireNonNull(executors, "script executors must not be null")) {
            Objects.requireNonNull(executor, "script executor must not be null");
            String language = ScriptExecutor.requireCanonicalName(executor.name());
            ScriptExecutor existing = resolved.putIfAbsent(language, executor);
            if (existing != null) {
                throw duplicateExecutor(language, existing, executor);
            }
        }
        return resolved;
    }

    private static ScriptExecutorRegistry assemble(ProcessEngineConfig config, Map<String, ScriptExecutor> configured) {
        ProcessEngineConfig engineConfig = Objects.requireNonNull(config, "config");
        Map<String, ScriptExecutor> resolved = new LinkedHashMap<>();
        List<QlExpressScriptExecutor> owned = new ArrayList<>();
        QlExpressScriptExecutor qlExecutor = new QlExpressScriptExecutor(engineConfig.getClassLoader());
        resolved.put("qlexpress", qlExecutor);
        resolved.put("java", new JavaSourceScriptExecutor());
        owned.add(qlExecutor);
        configured.forEach((language, executor) -> {
            ScriptExecutor existing = resolved.putIfAbsent(language, executor);
            if (existing != null) {
                owned.forEach(QlExpressScriptExecutor::close);
                throw duplicateExecutor(language, existing, executor);
            }
        });
        return new ScriptExecutorRegistry(resolved, owned);
    }

    private static CompileFlowException.ConfigurationException duplicateExecutor(String language,
            ScriptExecutor existing, ScriptExecutor duplicate) {
        return new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_001,
                "Duplicate script executors for language '" + language + "': " + existing.getClass().getName() + " and "
                + duplicate.getClass().getName());
    }

    /**
     * Looks up a registered script executor by language name.
     *
     * @param name script executor name
     * @return registered executor
     */
    public ScriptExecutor getScriptExecutor(String name) {
        String language = ScriptExecutor.requireCanonicalName(name);
        ScriptExecutor executor = scriptExecutorMap.get(language);
        if (executor == null) {
            throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_003,
                    "No script executor is registered for language '" + language + "'. Available languages: "
                    + scriptExecutorMap.keySet());
        }
        return executor;
    }

    /**
     * Returns the canonical language names available to process compilation.
     *
     * @return immutable language-name set
     */
    public Set<String> getLanguageNames() {
        return scriptExecutorMap.keySet();
    }

    /**
     * Validates one complete script program specification with its registered provider.
     */
    public void validate(ScriptProgramSpec spec) {
        Objects.requireNonNull(spec, "spec");
        ScriptExecutor executor = getScriptExecutor(spec.language());
        validate(spec, executor);
    }

    private static void validate(ScriptProgramSpec spec, ScriptExecutor executor) {
        try {
            executor.validate(spec);
        } catch (ScriptException classified) {
            throw classified;
        } catch (RuntimeException providerFailure) {
            throw new ScriptException(ScriptException.Kind.INVALID_SOURCE,
                    "Script source validation failed for language '" + spec.language() + "'", providerFailure);
        }
    }

    /**
     * Compiles a complete definition-owned script program for the requesting runtime.
     *
     * @param spec source and explicit declared signature
     * @return provider-owned disposable program
     */
    public ScriptProgram compile(ScriptProgramSpec spec) {
        Objects.requireNonNull(spec, "spec");
        ScriptExecutor executor = getScriptExecutor(spec.language());
        return compile(spec, executor);
    }

    /**
     * Evaluates one program owned by the active runtime.
     */
    public Object evaluate(ScriptProgram program, Map<String, Object> context) {
        ScriptProgram artifact = Objects.requireNonNull(program, "program");
        try {
            return getScriptExecutor(artifact.language())
                .evaluate(artifact, Objects.requireNonNull(context, "context"));
        } catch (ScriptException classified) {
            throw classified;
        } catch (RuntimeException providerFailure) {
            throw new ScriptException(ScriptException.Kind.EVALUATION_FAILED,
                    "Script evaluation failed for language '" + artifact.language() + "'", providerFailure);
        }
    }

    private ScriptProgram compile(ScriptProgramSpec spec, ScriptExecutor executor) {
        try {
            ScriptProgram program = Objects.requireNonNull(executor.compile(spec), "scriptProgram");
            String programLanguage = ScriptExecutor.requireCanonicalName(program.language());
            if (!spec.language().equals(programLanguage)) {
                throw new ScriptException(ScriptException.Kind.COMPILATION_FAILED,
                        "ScriptExecutor for language '" + spec.language() + "' returned a program for language '" + programLanguage + "'");
            }
            return program;
        } catch (ScriptException classified) {
            throw classified;
        } catch (RuntimeException providerFailure) {
            throw new ScriptException(ScriptException.Kind.COMPILATION_FAILED,
                    "Script compilation failed for language '" + spec.language() + "'", providerFailure);
        }
    }

    /**
     * Releases resources owned by core-provided executors without closing supplied executors.
     */
    @Override
    public void close() {
        ownedExecutors.forEach(QlExpressScriptExecutor::close);
    }
}
