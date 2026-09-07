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
import com.alibaba.compileflow.engine.core.source.ProcessDefinitionSnapshot;
import com.alibaba.compileflow.engine.core.source.FlowModelReader;
import com.alibaba.compileflow.engine.core.controlflow.StructuredControlFlowPlan;
import com.alibaba.compileflow.engine.core.controlflow.StructuredControlFlowAnalyzer;
import com.alibaba.compileflow.engine.core.validation.FlowModelValidator;
import com.alibaba.compileflow.engine.core.validation.ValidationFailure;
import com.alibaba.compileflow.engine.core.model.FlowModel;
import com.alibaba.compileflow.engine.core.model.Node;
import com.alibaba.compileflow.engine.core.model.NodeContainer;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Target-neutral source-validation and semantic-analysis pipeline shared by every runtime realization.
 *
 * @author yusu
 */
public final class ProcessSemanticCompiler<T extends FlowModel<?>> {
    private final FlowModelReader<T> modelReader;
    private final FlowModelValidator modelValidator;
    private final Function<T, ProcessSemanticPlan> semanticFrontend;
    private final StructuredControlFlowAnalyzer analyzer = new StructuredControlFlowAnalyzer();

    public ProcessSemanticCompiler(FlowModelReader<T> modelReader, FlowModelValidator modelValidator,
            Function<T, ProcessSemanticPlan> semanticFrontend) {
        this.modelReader = Objects.requireNonNull(modelReader, "modelReader");
        this.modelValidator = Objects.requireNonNull(modelValidator, "modelValidator");
        this.semanticFrontend = Objects.requireNonNull(semanticFrontend, "semanticFrontend");
    }

    public ProcessSemanticCompilation compile(ProcessDefinitionSnapshot definition) {
        T model = modelReader.read(Objects.requireNonNull(definition, "definition"));
        List<ValidationFailure> failures = modelValidator.validate(model);
        if (!failures.isEmpty()) {
            throw new CompileFlowException(ErrorCode.CF_VALIDATION_002,
                    "Validation failed: " + failures
                        .stream()
                        .map(ValidationFailure::message)
                        .collect(Collectors.joining("; ")));
        }
        ProcessSemanticPlan semanticPlan = semanticFrontend.apply(model);
        StructuredControlFlowPlan structuredPlan = analyzer.analyze(semanticPlan);
        return new ProcessSemanticCompilation(semanticPlan, structuredPlan, collectNodeNames(model));
    }

    private static Map<String, String> collectNodeNames(NodeContainer<?> model) {
        Map<String, String> names = new LinkedHashMap<>();
        Deque<NodeContainer<?>> pending = new ArrayDeque<>();
        pending.add(model);
        while (!pending.isEmpty()) {
            for (Node node : pending.removeFirst().getAllNodes()) {
                if (node.getName() != null && !node.getName().isBlank()) {
                    names.put(node.getId(), node.getName());
                }
                if (node instanceof NodeContainer<?> nested) {
                    pending.addLast(nested);
                }
            }
        }
        return Map.copyOf(names);
    }

    public record ProcessSemanticCompilation(ProcessSemanticPlan semanticPlan, StructuredControlFlowPlan structuredPlan,
            Map<String, String> nodeNames) {
        public ProcessSemanticCompilation(ProcessSemanticPlan semanticPlan, StructuredControlFlowPlan structuredPlan) {
            this(semanticPlan, structuredPlan, Map.of());
        }

        public ProcessSemanticCompilation {
            Objects.requireNonNull(semanticPlan, "semanticPlan");
            Objects.requireNonNull(structuredPlan, "structuredPlan");
            nodeNames = Map.copyOf(Objects.requireNonNull(nodeNames, "nodeNames"));
        }
    }
}
