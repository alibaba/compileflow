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
package com.alibaba.compileflow.durable.runtime.machine;

import com.alibaba.compileflow.engine.core.controlflow.StructuredControlFlowPlan;
import com.alibaba.compileflow.engine.core.controlflow.StructuredControlFlowAnalyzer;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptProgramCatalog;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.spi.script.ScriptProgram;
import com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Checks and lowers source-neutral Process semantics into the shared Durable machine.
 *
 * @author yusu
 */
public final class DurableProcessCompiler {
    private final StructuredControlFlowAnalyzer analyzer = new StructuredControlFlowAnalyzer();
    private final ScriptExecutorRegistry scriptExecutors;
    private final DurableProcessEligibilityChecker eligibilityChecker;
    private final DurableMachineLowerer machineLowerer;
    private final boolean deploymentCapabilities;

    public DurableProcessCompiler() {
        this(ScriptExecutorRegistry.from(List.of()));
    }

    public DurableProcessCompiler(ScriptExecutorRegistry scriptExecutors) {
        this(scriptExecutors, new DurableJavaExpressionValidator(), true);
    }

    DurableProcessCompiler(ScriptExecutorRegistry scriptExecutors, DurableExpressionValidator expressionValidator,
            boolean deploymentCapabilities) {
        this.scriptExecutors = Objects.requireNonNull(scriptExecutors, "scriptExecutors");
        eligibilityChecker = new DurableProcessEligibilityChecker(this.scriptExecutors, expressionValidator);
        machineLowerer = new DurableMachineLowerer(expressionValidator);
        this.deploymentCapabilities = deploymentCapabilities;
    }

    /**
     * Creates the compiler-free structural path used to decode persisted Process state.
     */
    public static DurableProcessCompiler structural() {
        return new DurableProcessCompiler(ScriptExecutorRegistry.from(List.of()),
                (expression, visibleTypes, targetType) -> List.of(), false);
    }

    /**
     * Returns deterministic eligibility without lowering an ineligible Process.
     */
    public DurableModelEligibility check(ProcessSemanticPlan semanticPlan) {
        ProcessSemanticPlan semantics = Objects.requireNonNull(semanticPlan, "semanticPlan");
        StructuredControlFlowPlan structure;
        try {
            structure = analyzer.analyze(semantics);
        } catch (RuntimeException failure) {
            return new DurableModelEligibility(List.of(
                    new DurableModelEligibility.Problem("DURABLE_CONTROL_FLOW_INVALID", null,
                            Objects.toString(failure.getMessage(), "Invalid control flow"))));
        }
        return deploymentCapabilities
                ? eligibilityChecker.check(semantics, structure)
                : eligibilityChecker.checkStructure(semantics, structure);
    }

    /**
     * Returns deterministic eligibility from the already analyzed shared structure.
     */
    public DurableModelEligibility check(ProcessSemanticPlan semanticPlan, StructuredControlFlowPlan structuredPlan) {
        ProcessSemanticPlan semantics = Objects.requireNonNull(semanticPlan, "semanticPlan");
        StructuredControlFlowPlan structure = Objects.requireNonNull(structuredPlan, "structuredPlan");
        return deploymentCapabilities
                ? eligibilityChecker.check(semantics, structure)
                : eligibilityChecker.checkStructure(semantics, structure);
    }

    /**
     * Lowers one eligible semantic plan exactly once.
     */
    public DurableMachinePlan lower(ProcessSemanticPlan semanticPlan) {
        ProcessSemanticPlan semantics = Objects.requireNonNull(semanticPlan, "semanticPlan");
        StructuredControlFlowPlan structure;
        try {
            structure = analyzer.analyze(semantics);
        } catch (RuntimeException failure) {
            throw new DurableModelEligibilityException(
                    new DurableModelEligibility(List.of(
                            new DurableModelEligibility.Problem("DURABLE_CONTROL_FLOW_INVALID", null,
                                    Objects.toString(failure.getMessage(), "Invalid control flow")))));
        }
        DurableModelEligibility eligibility = deploymentCapabilities
                ? eligibilityChecker.checkForLowering(semantics, structure)
                : eligibilityChecker.checkStructure(semantics, structure);
        eligibility.requireEligible();
        return machineLowerer.lower(semantics, structure);
    }

    /**
     * Lowers one eligible semantic plan using the shared structure analyzed by the source compiler.
     */
    public DurableMachinePlan lower(ProcessSemanticPlan semanticPlan, StructuredControlFlowPlan structuredPlan) {
        ProcessSemanticPlan semantics = Objects.requireNonNull(semanticPlan, "semanticPlan");
        StructuredControlFlowPlan structure = Objects.requireNonNull(structuredPlan, "structuredPlan");
        DurableModelEligibility eligibility = deploymentCapabilities
                ? eligibilityChecker.checkForLowering(semantics, structure)
                : eligibilityChecker.checkStructure(semantics, structure);
        eligibility.requireEligible();
        return machineLowerer.lower(semantics, structure);
    }

    /**
     * Compiles the disposable script programs owned by one exact Durable runtime.
     *
     * @param machinePlan lowered semantics for the exact Process version
     * @return immutable runtime-local artifacts keyed by their full script specification
     */
    public Map<ScriptProgramSpec, ScriptProgram> compileScripts(DurableMachinePlan machinePlan) {
        DurableMachinePlan plan = Objects.requireNonNull(machinePlan, "machinePlan");
        return ScriptProgramCatalog.compile(plan.semanticPlan(), scriptExecutors);
    }
}
