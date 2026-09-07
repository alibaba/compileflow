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
package com.alibaba.compileflow.engine.core.semantic;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.core.source.ProcessDefinitionSnapshot;
import com.alibaba.compileflow.engine.core.semantic.ProcessSemanticCompiler.ProcessSemanticCompilation;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Immutable selection of the installed semantic frontends for one ClassLoader scope.
 *
 * @author yusu
 */
public final class ProcessSemanticCompilerRegistry {
    private final Map<ProcessModelType, ProcessSemanticCompiler<?>> compilers;

    public ProcessSemanticCompilerRegistry(ClassLoader classLoader) {
        this(ServiceLoader.load(ProcessSemanticCompilerProvider.class,
                Objects.requireNonNull(classLoader, "classLoader")));
    }

    ProcessSemanticCompilerRegistry(Iterable<ProcessSemanticCompilerProvider> providers) {
        EnumMap<ProcessModelType, ProcessSemanticCompiler<?>> installed = new EnumMap<>(ProcessModelType.class);
        try {
            for (ProcessSemanticCompilerProvider provider : providers) {
                ProcessModelType type = Objects.requireNonNull(provider.getModelType(), "provider modelType");
                if (installed.containsKey(type)) {
                    throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_005,
                            "Multiple semantic compiler providers found for process model type: " + type);
                }
                installed.put(type,
                        Objects.requireNonNull(provider.createSemanticCompiler(),
                                "createSemanticCompiler must not return null"));
            }
        } catch (ServiceConfigurationError failure) {
            throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_005,
                    "Failed to discover semantic compiler providers", failure);
        }
        compilers = Map.copyOf(installed);
    }

    public ProcessSemanticCompilation compile(ProcessDefinitionSnapshot definition) {
        ProcessDefinitionSnapshot source = Objects.requireNonNull(definition, "definition");
        ProcessSemanticCompiler<?> compiler = compilers.get(source.getModelType());
        if (compiler == null) {
            throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_005,
                    "No semantic compiler provider found for process model type: " + source.getModelType()
                    + ". Ensure the corresponding format module is on the classpath.");
        }
        return compiler.compile(source);
    }
}
