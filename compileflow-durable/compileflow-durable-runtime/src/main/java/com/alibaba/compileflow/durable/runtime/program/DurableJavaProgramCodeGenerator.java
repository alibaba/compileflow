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
package com.alibaba.compileflow.durable.runtime.program;

import com.alibaba.compileflow.durable.runtime.kernel.BoundaryCompletion;
import com.alibaba.compileflow.durable.runtime.kernel.BoundaryKind;
import com.alibaba.compileflow.durable.runtime.kernel.BranchFrame;
import com.alibaba.compileflow.durable.runtime.kernel.BranchActivation;
import com.alibaba.compileflow.durable.runtime.kernel.ConcurrentBranchFrame;
import com.alibaba.compileflow.durable.runtime.kernel.ConcurrentFrontierOperations;
import com.alibaba.compileflow.durable.runtime.kernel.ContinuationSnapshot;
import com.alibaba.compileflow.durable.runtime.kernel.ForEachFrame;
import com.alibaba.compileflow.durable.runtime.kernel.FrontierExecutionOperations;
import com.alibaba.compileflow.durable.runtime.kernel.FrontierId;
import com.alibaba.compileflow.durable.runtime.kernel.FrontierSnapshot;
import com.alibaba.compileflow.durable.runtime.kernel.FrontierStepResult;
import com.alibaba.compileflow.durable.runtime.kernel.MachineTurnResult;
import com.alibaba.compileflow.durable.runtime.kernel.MachineTurnScheduler;
import com.alibaba.compileflow.durable.runtime.kernel.MultiInstanceFrontierOperations;
import com.alibaba.compileflow.durable.runtime.kernel.OccurrenceKey;
import com.alibaba.compileflow.durable.runtime.kernel.ParallelForEachFrame;
import com.alibaba.compileflow.durable.runtime.kernel.OccurrenceResult;
import com.alibaba.compileflow.durable.runtime.kernel.ResumeDescriptor;
import com.alibaba.compileflow.durable.runtime.kernel.ResumePoint;
import com.alibaba.compileflow.durable.runtime.kernel.ScopeFrame;
import com.alibaba.compileflow.durable.runtime.kernel.SemanticCheckpoint;
import com.alibaba.compileflow.durable.runtime.kernel.TimerRequest;
import com.alibaba.compileflow.durable.runtime.kernel.TurnBudget;
import com.alibaba.compileflow.durable.runtime.kernel.WaitRequest;
import com.alibaba.compileflow.durable.runtime.kernel.WhileFrame;
import com.alibaba.compileflow.durable.runtime.machine.BoundExpression;
import com.alibaba.compileflow.durable.runtime.machine.DurableMachinePlan;
import com.alibaba.compileflow.engine.core.java.codegen.JavaIdentifiers;
import com.alibaba.compileflow.engine.core.java.codegen.JavaTypeName;
import com.alibaba.compileflow.engine.core.model.action.ActionExecution;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.AwaitPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.IterationPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.core.type.JavaSourceLiteral;
import com.alibaba.compileflow.engine.core.semantic.plan.TimerPlan;
import com.alibaba.compileflow.engine.core.runtime.execution.ConditionSemantics;
import com.alibaba.compileflow.engine.core.type.DataTypes;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.annotation.processing.Generated;

/**
 * Generates a compiled switch state machine from one immutable Durable Machine Plan.
 *
 * @author yusu
 */
final class DurableJavaProgramCodeGenerator {
    private static final String GENERATED_PACKAGE = "com.alibaba.compileflow.generated.durable.process";
    private static final int PLAN_DIGEST_PREFIX_LENGTH = 16;
    private static final int MAX_READABLE_PROCESS_CODE_LENGTH = 160;
    private static final int MAX_READABLE_METHOD_ID_LENGTH = 80;
    /*
     * JVM limits one method's Code attribute to 65,535 bytes. The generated state machine keeps
     * one iterative control loop, but emits node and resume bodies into bounded helpers so
     * a large otherwise-valid process never fails only because one switch became too large.
     */
    private static final int MAX_CASES_PER_GENERATED_METHOD = 128;
    private final DurableMachinePlan machinePlan;
    private final ProcessSemanticPlan semanticPlan;
    private final Map<String, ProcessSemanticPlan.VariablePlan> stateVariables;
    private final ClassLoader classLoader;
    /*
     * Generated source must be a pure function of the Durable Machine Plan. Its digest uses a
     * sorted canonical form, so insertion-ordered traversal here would let equivalent plans emit
     * different source and break reproducible builds.
     * Sorted maps keep helper numbering and case order aligned with the digest.
     */
    private final Map<String, ExpressionMethod> expressionMethods = new TreeMap<>();
    private final Map<String, JavaTypeName> importedTypes = new TreeMap<>();
    private final Map<String, JavaTypeName> referencedTypesBySimpleName = new TreeMap<>();
    private final String className;

    DurableJavaProgramCodeGenerator(DurableMachinePlan machinePlan) {
        this(machinePlan, contextClassLoader());
    }

    DurableJavaProgramCodeGenerator(DurableMachinePlan machinePlan, ClassLoader classLoader) {
        this.machinePlan = Objects.requireNonNull(machinePlan, "machinePlan");
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
        this.semanticPlan = machinePlan.semanticPlan();
        this.stateVariables = semanticPlan.getVariables();
        this.className = generatedClassName(semanticPlan.getProcessCode(), machinePlan.digest());
        collectExpressions();
        collectGeneratedTypes();
    }

    private static String generatedClassName(String processCode, String planDigest) {
        String readableCode = JavaIdentifiers.toClassName(processCode);
        int codePointCount = readableCode.codePointCount(0, readableCode.length());
        if (codePointCount > MAX_READABLE_PROCESS_CODE_LENGTH) {
            readableCode = readableCode.substring(0,
                    readableCode.offsetByCodePoints(0, MAX_READABLE_PROCESS_CODE_LENGTH));
        }
        String digest = planDigest.substring(0, PLAN_DIGEST_PREFIX_LENGTH);
        return "Durable_" + readableCode + "_" + digest;
    }

    private static ClassLoader contextClassLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader == null ? DurableJavaProgramCodeGenerator.class.getClassLoader() : loader;
    }

    private static String safeComment(String value) {
        StringBuilder safe = new StringBuilder(value.length());
        value
            .codePoints()
            .forEach(codePoint -> safe.appendCodePoint(
                    Character.isISOControl(codePoint) || Character.getType(codePoint) == Character.FORMAT ? ' ' : codePoint));
        return safe.toString();
    }

    /**
     * Returns map values ordered by key so generated source depends only on the canonical
     * plan, never on model insertion order.
     */
    private static <V> List<V> sortedValues(Map<String, V> source) {
        return sortedByKey(source).stream().map(Map.Entry::getValue).toList();
    }

    /**
     * Returns map entries ordered by key. See {@link #sortedValues(Map)}.
     */
    private static <V> List<Map.Entry<String, V>> sortedByKey(Map<String, V> source) {
        return source.entrySet().stream().sorted(Map.Entry.comparingByKey()).collect(Collectors.toList());
    }

    private List<Map.Entry<String, ResumeDescriptor>> boundaryResumes() {
        return sortedByKey(machinePlan.resumes())
            .stream()
            .filter(entry -> entry.getValue().resumePoint().isAfterElement())
            .toList();
    }

    private static <T> List<List<T>> partition(List<T> values) {
        List<List<T>> result = new ArrayList<>();
        for (int start = 0; start < values.size(); start += MAX_CASES_PER_GENERATED_METHOD) {
            int end = Math.min(start + MAX_CASES_PER_GENERATED_METHOD, values.size());
            result.add(List.copyOf(values.subList(start, end)));
        }
        return result;
    }

    private static boolean usesStateBindings(List<BoundExpression.Binding> bindings) {
        return bindings
            .stream()
            .anyMatch(binding -> binding.source() == BoundExpression.Binding.Source.STATE);
    }

    private static boolean usesLexicalBindings(List<BoundExpression.Binding> bindings) {
        return bindings
            .stream()
            .anyMatch(binding -> binding.source() == BoundExpression.Binding.Source.FRAME);
    }

    private static String conditionKey(String nodeId, String kind) {
        return nodeId + "#" + kind;
    }

    private static String transitionConditionKey(String nodeId, int index) {
        return nodeId + "#transition#" + index;
    }

    private static String semanticMethodName(String role, String identity) {
        String readable = JavaIdentifiers.toClassName(identity);
        int codePoints = readable.codePointCount(0, readable.length());
        if (codePoints > MAX_READABLE_METHOD_ID_LENGTH) {
            readable = readable.substring(0, readable.offsetByCodePoints(0, MAX_READABLE_METHOD_ID_LENGTH));
        }
        String digest = JavaIdentifiers.toStableMethodSuffix(identity).substring(1, 13);
        return role + readable + "_" + digest;
    }

    private static String stringSet(Set<String> values) {
        if (values.isEmpty()) {
            return "Set.of()";
        }
        return "Set.of("
                + values.stream().map(DurableJavaProgramCodeGenerator::literal).collect(Collectors.joining(", ")) + ")";
    }

    private static String branchActivations(List<BranchActivation> values) {
        return "List.of("
                + values
                    .stream()
                    .map(value -> "new BranchActivation(" + value.ordinal() + ", " + literal(value.branchStartId())
                            + ")")
                    .collect(Collectors.joining(", ")) + ")";
    }

    private static String literalOrNull(String value) {
        return value == null ? "null" : literal(value);
    }

    private static String literal(String value) {
        return JavaSourceLiteral.stringExpression(value);
    }

    private static void line(StringBuilder code, String line) {
        code.append(line).append('\n');
    }

    private static String shortTypeName(String sourceTypeName) {
        return JavaTypeName.of(sourceTypeName).getShortName();
    }

    private static boolean isJdkType(JavaTypeName type) {
        String importName = type.getImportName();
        return importName.startsWith("java.") || importName.startsWith("javax.");
    }

    String getClassFullName() {
        return GENERATED_PACKAGE + "." + className;
    }

    String generateCode() {
        StringBuilder code = new StringBuilder();
        line(code, "package " + GENERATED_PACKAGE + ";");
        line(code, "");
        line(code, "// Generated Durable process: " + safeComment(semanticPlan.getProcessCode()));
        line(code, "// DurableMachinePlan digest: " + machinePlan.digest());
        line(code, "// Generator version: " + DurableJavaProgramCompiler.GENERATOR_VERSION);
        line(code, "");
        generateImports(code);
        line(code,
                "@Generated(value = " + literal(DurableJavaProgramCompiler.GENERATOR_VERSION) + ", comments = "
                + literal("process=" + semanticPlan.getProcessCode()) + ")");
        line(code, "public final class " + className + " implements DurableProgram {");
        line(code, "  private static final Set<String> ALLOWED_FIELDS = " + stringSet(stateVariables.keySet()) + ";");
        generateAdvance(code);
        generateInitialState(code);
        generateAdvanceFrontier(code);
        if (!machinePlan.iterations().isEmpty()) {
            generateLoopAdvance(code);
        }
        if (hasBreakControl()) {
            generateLoopBreak(code);
        }
        if (requiresLexicalBindings()) {
            generateLexicalBindings(code);
        }
        generateOutput(code);
        generateExpressionMethods(code);
        removeTrailingBlankLines(code);
        line(code, "}");
        return code.toString();
    }

    private static void removeTrailingBlankLines(StringBuilder code) {
        while (code.length() >= 2 && code.charAt(code.length() - 1) == '\n' && code.charAt(code.length() - 2) == '\n') {
            code.setLength(code.length() - 1);
        }
    }

    private void generateImports(StringBuilder code) {
        List<JavaTypeName> imports = new ArrayList<>(importedTypes.values());
        for (int index = 0; index < imports.size(); index++) {
            JavaTypeName importedType = imports.get(index);
            if (index > 0 && isJdkType(importedType) != isJdkType(imports.get(index - 1))) {
                line(code, "");
            }
            line(code, "import " + importedType.getImportName() + ";");
        }
        if (!imports.isEmpty()) {
            line(code, "");
        }
    }

    private void generateAdvance(StringBuilder code) {
        line(code, "");
        line(code, "  @Override");
        line(code, "  public MachineTurnResult advance(");
        line(code, "      ContinuationSnapshot continuation,");
        line(code, "      List<OccurrenceResult> availableResults,");
        line(code, "      TurnBudget budget,");
        line(code, "      DurableExecutionContext context) throws Exception {");
        line(code, "    Objects.requireNonNull(continuation, \"continuation\");");
        line(code, "    Objects.requireNonNull(availableResults, \"availableResults\");");
        line(code, "    Objects.requireNonNull(budget, \"budget\");");
        line(code, "    Objects.requireNonNull(context, \"context\");");
        line(code, "    MachineTurnScheduler.Selection _cf$selection = MachineTurnScheduler.select(");
        line(code, "        continuation, availableResults, budget.maxActiveIterations());");
        line(code, "    FrontierSnapshot _cf$active = _cf$selection.frontier();");
        line(code, "    OccurrenceResult _cf$resolved = _cf$selection.occurrenceResult();");
        line(code, "    List<FrontierSnapshot> _cf$frontiers =");
        line(code, "        new ArrayList<>(_cf$selection.remainingFrontiers());");
        line(code, "    if (_cf$active.multiInstanceController() != null) {");
        line(code, "      if (!budget.tryConsumeStep()) {");
        line(code, "        return new MachineTurnResult(");
        line(code, "            new FrontierStepResult.Yielded(");
        line(code, "                new SemanticCheckpoint(_cf$active.resumePoint(), _cf$active.scopeFrames()),");
        line(code, "                _cf$active.variables()),");
        line(code, "            _cf$active.frontierId(), continuation, List.of());");
        line(code, "      }");
        line(code, "      _cf$frontiers.add(_cf$active);");
        line(code, "      ContinuationSnapshot _cf$issued =");
        line(code, "          new ContinuationSnapshot(_cf$issueMultiInstance(");
        line(code, "              _cf$frontiers, _cf$active, budget.maxActiveIterations()));");
        line(code, "      return new MachineTurnResult(");
        line(code, "          new FrontierStepResult.Advanced(), _cf$active.frontierId(), _cf$issued, List.of());");
        line(code, "    }");
        line(code, "    SemanticCheckpoint checkpoint = new SemanticCheckpoint(");
        line(code, "        _cf$active.resumePoint(), _cf$active.scopeFrames());");
        line(code,
                "    Map<String, Object> _cf$state = FrontierExecutionOperations.mutableState(_cf$active.variables());");
        line(code,
                "    List<ScopeFrame> _cf$frames = FrontierExecutionOperations.mutableFrames(_cf$active.scopeFrames());");
        line(code, "    Set<String> _cf$writes = new LinkedHashSet<>();");
        line(code, "    FrontierStepResult _cf$outcome;");
        line(code, "    List<OccurrenceKey> _cf$consumed = List.of();");
        line(code, "    String _cf$activeJoin = null;");
        line(code, "    List<BranchFrame> _cf$ancestry = _cf$active.branchFrames();");
        line(code, "    if (!_cf$ancestry.isEmpty()");
        line(code, "        && _cf$ancestry.get(_cf$ancestry.size() - 1)");
        line(code, "            instanceof ConcurrentBranchFrame _cf$concurrent) {");
        line(code, "      _cf$activeJoin = _cf$concurrent.joinId();");
        line(code, "    }");
        line(code, "    if (checkpoint.resumePoint().isStart()) {");
        line(code, "      if (_cf$resolved != null) {");
        line(code, "        throw new IllegalArgumentException(\"START cannot consume an occurrence result\");");
        line(code, "      }");
        line(code, "      _cf$initialize(_cf$state);");
        line(code, "      _cf$outcome = advanceFrontier(");
        line(code, "          " + literal(machinePlan.entryNodeId()) + ", _cf$state, _cf$frames, _cf$writes,");
        line(code, "          _cf$activeJoin, budget, context);");
        line(code, "    } else if (checkpoint.resumePoint().isBeforeElement()");
        line(code, "        || checkpoint.resumePoint().isBeforeIterationBody()) {");
        line(code, "      if (_cf$resolved != null) {");
        line(code,
                "        throw new IllegalArgumentException(\"A before coordinate cannot consume an occurrence result\");");
        line(code, "      }");
        line(code, "      _cf$outcome = advanceFrontier(");
        line(code, "          checkpoint.resumePoint().elementId(), _cf$state, _cf$frames, _cf$writes,");
        line(code, "          _cf$activeJoin, budget, context);");
        line(code, "    } else if (checkpoint.resumePoint().isAfterElement()) {");
        line(code, "      if (_cf$resolved == null) {");
        line(code, "        throw new IllegalArgumentException(\"AFTER_ELEMENT requires its occurrence result\");");
        line(code, "      }");
        line(code, "      BoundaryCompletion boundaryCompletion = _cf$resolved.completion();");
        line(code, "      if (!boundaryCompletion.boundaryId().equals(checkpoint.resumePoint().elementId())) {");
        line(code, "        throw new IllegalArgumentException(\"Occurrence result does not match continuation\");");
        line(code, "      }");
        List<List<Map.Entry<String, ResumeDescriptor>>> resumeChunks = partition(boundaryResumes());
        if (resumeChunks.isEmpty()) {
            line(code, "      throw new IllegalArgumentException(\"Process has no resumable occurrences\");");
        } else {
            line(code, "      String _cf$next;");
            line(code, "      switch (_cf$resumeChunk(checkpoint.resumePoint().key())) {");
            for (int index = 0; index < resumeChunks.size(); index++) {
                line(code, "        case " + index + ":");
                line(code,
                        "          _cf$next = _cf$resumeChunk" + index
                        + "(checkpoint, boundaryCompletion, context, _cf$state, _cf$frames, _cf$writes);");
                line(code, "          break;");
            }
            line(code, "        default:");
            line(code,
                    "          throw new IllegalArgumentException(\"Unknown ResumePoint: \" + checkpoint.resumePoint());");
            line(code, "      }");
            line(code, "      _cf$outcome = advanceFrontier(");
            line(code, "          _cf$next, _cf$state, _cf$frames, _cf$writes,");
            line(code, "          _cf$activeJoin, budget, context);");
            line(code, "      _cf$consumed = List.of(_cf$resolved.occurrence());");
        }
        line(code, "    } else {");
        line(code, "      throw new IllegalArgumentException(\"A parked join frontier is not executable\");");
        line(code, "    }");
        line(code, "    return _cf$completeTurn(");
        line(code, "        _cf$active, _cf$frontiers, _cf$outcome, _cf$writes, _cf$consumed, budget);");
        line(code, "  }");
        generateCompleteTurn(code);
        generateMultiInstanceCoordinator(code);
        if (!resumeChunks.isEmpty()) {
            generateResumeChunkLookup(code, resumeChunks);
            for (int index = 0; index < resumeChunks.size(); index++) {
                generateResumeChunk(code, index, resumeChunks.get(index));
            }
        }
    }

    private void generateInitialState(StringBuilder code) {
        line(code, "");
        line(code, "  private void _cf$initialize(Map<String, Object> _cf$state) {");
        for (Map.Entry<String, ProcessSemanticPlan.VariablePlan> entry : sortedByKey(stateVariables)) {
            DataTypes.DefaultValueCode defaultValue = DataTypes.generateDefaultValueCode(DataTypes.getJavaClass(entry
                                .getValue()
                                .dataType(), classLoader), entry.getValue().defaultValue());
            line(code, "    if (!_cf$state.containsKey(" + literal(entry.getKey()) + ")) {");
            line(code, "      _cf$state.put(" + literal(entry.getKey()) + ", " + defaultValue.expression() + ");");
            line(code, "    }");
        }
        line(code, "  }");
    }

    private void generateCompleteTurn(StringBuilder code) {
        line(code, "");
        line(code, "  private MachineTurnResult _cf$completeTurn(");
        line(code, "      FrontierSnapshot _cf$active,");
        line(code, "      List<FrontierSnapshot> _cf$frontiers,");
        line(code, "      FrontierStepResult _cf$outcome,");
        line(code, "      Set<String> _cf$writes,");
        line(code, "      List<OccurrenceKey> _cf$consumed,");
        line(code, "      TurnBudget _cf$budget) {");
        line(code, "    if (_cf$outcome instanceof FrontierStepResult.Completed");
        line(code, "        || _cf$outcome instanceof FrontierStepResult.Failed) {");
        line(code, "      if (!_cf$active.branchFrames().isEmpty() || !_cf$frontiers.isEmpty()) {");
        line(code, "        throw new IllegalStateException(\"A concurrent branch cannot terminate the Process\");");
        line(code, "      }");
        line(code, "      return new MachineTurnResult(");
        line(code, "          _cf$outcome, _cf$active.frontierId(), null, _cf$consumed);");
        line(code, "    }");
        line(code, "    if (_cf$outcome instanceof FrontierStepResult.Forked _cf$forked) {");
        line(code, "      FrontierSnapshot _cf$parent = ConcurrentFrontierOperations.progress(");
        line(code, "          _cf$active, ResumePoint.beforeElement(_cf$forked.splitId()),");
        line(code, "          _cf$forked.state(), _cf$forked.scopeFrames(), _cf$writes);");
        line(code, "      _cf$frontiers.addAll(ConcurrentFrontierOperations.fork(");
        line(code, "          _cf$parent, _cf$forked.splitId(), _cf$forked.joinId(),");
        line(code, "          _cf$forked.selectedActivations()));");
        line(code, "      return new MachineTurnResult(");
        line(code, "          new FrontierStepResult.Advanced(), _cf$active.frontierId(),");
        line(code, "          new ContinuationSnapshot(_cf$frontiers), _cf$consumed);");
        line(code, "    }");
        line(code, "    if (_cf$outcome instanceof FrontierStepResult.ForkedEach _cf$forked) {");
        line(code, "      FrontierSnapshot _cf$parent = ConcurrentFrontierOperations.progress(");
        line(code, "          _cf$active, ResumePoint.beforeElement(_cf$forked.loopId()),");
        line(code, "          _cf$forked.state(), _cf$forked.scopeFrames(), _cf$writes);");
        line(code, "      _cf$frontiers = new ArrayList<>(_cf$startMultiInstance(");
        line(code, "          _cf$frontiers, _cf$parent, _cf$forked, _cf$budget.maxActiveIterations()));");
        line(code, "      return new MachineTurnResult(");
        line(code, "          new FrontierStepResult.Advanced(), _cf$active.frontierId(),");
        line(code, "          new ContinuationSnapshot(_cf$frontiers), _cf$consumed);");
        line(code, "    }");
        line(code, "    if (_cf$outcome instanceof FrontierStepResult.AtIterationEnd _cf$iteration) {");
        line(code, "      _cf$frontiers = new ArrayList<>(_cf$completeMultiInstance(");
        line(code, "          _cf$frontiers, _cf$active, _cf$iteration, _cf$budget.maxActiveIterations()));");
        line(code, "      return new MachineTurnResult(");
        line(code, "          new FrontierStepResult.Advanced(), _cf$active.frontierId(),");
        line(code, "          new ContinuationSnapshot(_cf$frontiers), _cf$consumed);");
        line(code, "    }");
        line(code, "    if (_cf$outcome instanceof FrontierStepResult.AtJoin _cf$atJoin) {");
        line(code, "      FrontierSnapshot _cf$parked = ConcurrentFrontierOperations.parkAtJoin(");
        line(code, "          _cf$active, _cf$atJoin.joinId(), _cf$atJoin.state(),");
        line(code, "          _cf$atJoin.scopeFrames(), _cf$writes);");
        line(code, "      _cf$frontiers.add(_cf$parked);");
        line(code, "      _cf$frontiers = new ArrayList<>(ConcurrentFrontierOperations.mergeReadyGroup(");
        line(code, "          _cf$frontiers, _cf$parked, _cf$atJoin.joinId()));");
        line(code, "      return new MachineTurnResult(");
        line(code, "          new FrontierStepResult.Advanced(), _cf$active.frontierId(),");
        line(code, "          new ContinuationSnapshot(_cf$frontiers), _cf$consumed);");
        line(code, "    }");
        line(code, "    if (_cf$outcome instanceof FrontierStepResult.Advanced) {");
        line(code, "      throw new IllegalStateException(");
        line(code, "          \"Generated run returned an internal coordinator outcome\");");
        line(code, "    }");
        line(code, "    SemanticCheckpoint _cf$checkpoint;");
        line(code, "    Map<String, Object> _cf$state;");
        line(code, "    if (_cf$outcome instanceof FrontierStepResult.Yielded _cf$yielded) {");
        line(code, "      _cf$checkpoint = _cf$yielded.checkpoint();");
        line(code, "      _cf$state = _cf$yielded.state();");
        line(code, "    } else if (_cf$outcome instanceof FrontierStepResult.Waiting _cf$waiting) {");
        line(code, "      _cf$checkpoint = _cf$waiting.checkpoint();");
        line(code, "      _cf$state = _cf$waiting.state();");
        line(code, "    } else if (_cf$outcome instanceof FrontierStepResult.TimerWaiting _cf$waiting) {");
        line(code, "      _cf$checkpoint = _cf$waiting.checkpoint();");
        line(code, "      _cf$state = _cf$waiting.state();");
        line(code, "    } else if (_cf$outcome instanceof FrontierStepResult.EffectWaiting _cf$waiting) {");
        line(code, "      _cf$checkpoint = _cf$waiting.checkpoint();");
        line(code, "      _cf$state = _cf$waiting.state();");
        line(code, "    } else if (_cf$outcome instanceof FrontierStepResult.ProcessCallRequested _cf$requested) {");
        line(code, "      _cf$checkpoint = _cf$requested.checkpoint();");
        line(code, "      _cf$state = _cf$requested.state();");
        line(code, "    } else {");
        line(code, "      throw new IllegalStateException(\"Unknown generated Process outcome: \" + _cf$outcome);");
        line(code, "    }");
        line(code, "    _cf$frontiers.add(ConcurrentFrontierOperations.progress(");
        line(code, "        _cf$active, _cf$checkpoint.resumePoint(), _cf$state,");
        line(code, "        _cf$checkpoint.scopeFrames(), _cf$writes));");
        line(code, "    return new MachineTurnResult(");
        line(code, "        _cf$outcome, _cf$active.frontierId(),");
        line(code, "        new ContinuationSnapshot(_cf$frontiers), _cf$consumed);");
        line(code, "  }");
    }

    private void generateMultiInstanceCoordinator(StringBuilder code) {
        List<Map.Entry<String, DurableMachinePlan.Iteration.ForEach>> loops = sortedByKey(machinePlan.iterations())
            .stream()
            .filter(entry -> entry.getValue() instanceof DurableMachinePlan.Iteration.ForEach loop
                    && loop.execution() == IterationPlan.Execution.PARALLEL)
            .map(entry -> Map.entry(entry.getKey(), (DurableMachinePlan.Iteration.ForEach) entry.getValue()))
            .toList();
        line(code, "");
        line(code, "  private List<FrontierSnapshot> _cf$startMultiInstance(");
        line(code, "      List<FrontierSnapshot> _cf$frontiers, FrontierSnapshot _cf$parent,");
        line(code, "      FrontierStepResult.ForkedEach _cf$forked, int _cf$maxActive) {");
        if (loops.isEmpty()) {
            line(code, "    throw new IllegalArgumentException(\"Process declares no parallel foreach scope\");");
        } else {
            line(code, "    return switch (_cf$forked.loopId()) {");
            for (Map.Entry<String, DurableMachinePlan.Iteration.ForEach> entry : loops) {
                String loopId = entry.getKey();
                DurableMachinePlan.Iteration.ForEach loop = entry.getValue();
                line(code, "      case " + literal(loopId) + " -> MultiInstanceFrontierOperations.start(");
                line(code, "          _cf$frontiers, _cf$parent, " + literal(loopId) + ",");
                line(code,
                        "          " + literal(bodyStart(loopId, loop.body())) + ", "
                        + literal(loop.collectionVariable()) + ", " + literalOrNull(loop.collectionOwnerIterationId())
                        + ", " + literalOrNull(loop.outputSourceVariable()) + ", " + outputSourceInitializer(loop)
                        + ", _cf$forked.snapshot(), _cf$maxActive);");
            }
            line(code, "      default -> throw new IllegalArgumentException(\"Unknown parallel foreach: \"");
            line(code, "          + _cf$forked.loopId());");
            line(code, "    };");
        }
        line(code, "  }");

        line(code, "");
        line(code, "  private List<FrontierSnapshot> _cf$issueMultiInstance(");
        line(code, "      List<FrontierSnapshot> _cf$frontiers, FrontierSnapshot _cf$controller,");
        line(code, "      int _cf$maxActive) {");
        if (loops.isEmpty()) {
            line(code, "    throw new IllegalArgumentException(\"Process declares no parallel foreach scope\");");
        } else {
            line(code, "    return switch (_cf$controller.multiInstanceController().loopId()) {");
            for (Map.Entry<String, DurableMachinePlan.Iteration.ForEach> entry : loops) {
                String loopId = entry.getKey();
                DurableMachinePlan.Iteration.ForEach loop = entry.getValue();
                line(code, "      case " + literal(loopId) + " -> MultiInstanceFrontierOperations.issueAvailable(");
                line(code, "          _cf$frontiers, _cf$controller, " + literal(bodyStart(loopId, loop.body())) + ",");
                line(code,
                        "          " + literal(loop.collectionVariable()) + ", "
                        + literalOrNull(loop.collectionOwnerIterationId()) + ", "
                        + literalOrNull(loop.outputSourceVariable()) + ", " + outputSourceInitializer(loop)
                        + ", _cf$maxActive);");
            }
            line(code, "      default -> throw new IllegalArgumentException(");
            line(code, "          \"Unknown parallel foreach controller\");");
            line(code, "    };");
        }
        line(code, "  }");

        line(code, "");
        line(code, "  private List<FrontierSnapshot> _cf$completeMultiInstance(");
        line(code, "      List<FrontierSnapshot> _cf$frontiers, FrontierSnapshot _cf$iteration,");
        line(code, "      FrontierStepResult.AtIterationEnd _cf$outcome, int _cf$maxActive) {");
        if (loops.isEmpty()) {
            line(code, "    throw new IllegalArgumentException(\"Process declares no parallel foreach scope\");");
        } else {
            line(code, "    return switch (_cf$outcome.loopId()) {");
            for (Map.Entry<String, DurableMachinePlan.Iteration.ForEach> entry : loops) {
                String loopId = entry.getKey();
                DurableMachinePlan.Iteration.ForEach loop = entry.getValue();
                line(code, "      case " + literal(loopId) + " -> MultiInstanceFrontierOperations.completeIteration(");
                line(code, "          _cf$frontiers, _cf$iteration, " + literal(loopId) + ",");
                line(code,
                        "          " + literal(bodyStart(loopId, loop.body())) + ", "
                        + literal(loop.collectionVariable()) + ", " + literalOrNull(loop.collectionOwnerIterationId())
                        + ",");
                line(code,
                        "          " + literalOrNull(loop.outputSourceVariable()) + ", "
                        + literalOrNull(loop.outputTargetVariable()) + ", " + outputSourceInitializer(loop)
                        + ", _cf$outcome.state(), _cf$maxActive,");
                line(code,
                        "          (_cf$completedState, _cf$completedFrames, _cf$completedWrites) -> "
                        + nextExpression(loop.exit(), "_cf$completedState", "_cf$completedFrames", "_cf$completedWrites")
                        + ");");
            }
            line(code, "      default -> throw new IllegalArgumentException(\"Unknown parallel iteration end\");");
            line(code, "    };");
        }
        line(code, "  }");
    }

    private void generateAdvanceFrontier(StringBuilder code) {
        List<List<Map.Entry<String, DurableMachinePlan.Step>>> nodeChunks = partition(sortedByKey(machinePlan.steps()));
        line(code, "");
        line(code, "  private FrontierStepResult advanceFrontier(");
        line(code, "      String initialNodeId,");
        line(code, "      Map<String, Object> _cf$state,");
        line(code, "      List<ScopeFrame> _cf$frames,");
        line(code, "      Set<String> _cf$writes,");
        line(code, "      String _cf$activeJoin,");
        line(code, "      TurnBudget budget,");
        line(code, "      DurableExecutionContext context) throws Exception {");
        line(code, "    _cf$FrontierCursor _cf$cursor = new _cf$FrontierCursor(initialNodeId);");
        line(code, "    while (true) {");
        line(code, "      if (_cf$cursor.nodeId.startsWith(FrontierStepResult.WHILE_FAILURE_PREFIX)) {");
        line(code, "        return FrontierStepResult.whileMaxIterationsExceeded(");
        line(code, "            _cf$cursor.nodeId.substring(FrontierStepResult.WHILE_FAILURE_PREFIX.length()));");
        line(code, "      }");
        line(code, "      if (_cf$cursor.nodeId.startsWith(\"\\u0000parallel-end:\")) {");
        line(code, "        return new FrontierStepResult.AtIterationEnd(");
        line(code, "            _cf$cursor.nodeId.substring(14), _cf$state, _cf$frames);");
        line(code, "      }");
        line(code, "      if (!budget.tryConsumeStep()) {");
        line(code, "        return new FrontierStepResult.Yielded(");
        line(code, "            SemanticCheckpoint.beforeElement(_cf$cursor.nodeId, _cf$frames), _cf$state);");
        line(code, "      }");
        line(code, "      FrontierStepResult _cf$stepResult;");
        line(code, "      switch (_cf$stepChunk(_cf$cursor.nodeId)) {");
        for (int index = 0; index < nodeChunks.size(); index++) {
            line(code, "        case " + index + ":");
            line(code,
                    "          _cf$stepResult = _cf$stepChunk" + index
                    + "(_cf$cursor, _cf$state, _cf$frames, _cf$writes, _cf$activeJoin, context);");
            line(code, "          break;");
        }
        line(code, "        default:");
        line(code, "          throw new IllegalArgumentException(\"Unknown node: \" + _cf$cursor.nodeId);");
        line(code, "      }");
        line(code, "      if (_cf$stepResult != null) {");
        line(code, "        return _cf$stepResult;");
        line(code, "      }");
        line(code, "    }");
        line(code, "  }");
        generateFrontierCursor(code);
        generateStepChunkLookup(code, nodeChunks);
        for (int index = 0; index < nodeChunks.size(); index++) {
            generateStepChunk(code, index, nodeChunks.get(index));
        }
    }

    private void generateResumeChunk(StringBuilder code, int chunkIndex,
            List<Map.Entry<String, ResumeDescriptor>> resumes) {
        line(code, "");
        line(code, "  private String _cf$resumeChunk" + chunkIndex + "(");
        line(code, "      SemanticCheckpoint checkpoint,");
        line(code, "      BoundaryCompletion boundaryCompletion,");
        line(code, "      DurableExecutionContext context,");
        line(code, "      Map<String, Object> _cf$state,");
        line(code, "      List<ScopeFrame> _cf$frames,");
        line(code, "      Set<String> _cf$writes) throws Exception {");
        line(code, "    switch (checkpoint.resumePoint().key()) {");
        for (Map.Entry<String, ResumeDescriptor> resume : resumes) {
            generateResumeCase(code, resume);
        }
        line(code, "      default:");
        line(code, "        throw new IllegalArgumentException(");
        line(code, "            \"Unknown ResumePoint: \" + checkpoint.resumePoint());");
        line(code, "    }");
        line(code, "  }");
    }

    private void generateResumeCase(StringBuilder code, Map.Entry<String, ResumeDescriptor> entry) {
        ResumeDescriptor descriptor = entry.getValue();
        ResumePoint point = descriptor.resumePoint();
        String boundaryId = point.elementId();
        line(code, "      case " + literal(entry.getKey()) + ": {");
        generateFrameValidation(code, descriptor);
        if (descriptor.boundaryKind() == BoundaryKind.WAIT) {
            AwaitPlan await = (AwaitPlan) semanticPlan.requireNode(boundaryId).operation();
            line(code, "        if (boundaryCompletion instanceof BoundaryCompletion.WaitCompleted waitResult) {");
            String event = await.event();
            if (event != null) {
                line(code, "          if (!" + literal(event) + ".equals(waitResult.event())) {");
                line(code, "            throw new IllegalArgumentException(\"Wait event does not match boundary\");");
                line(code, "          }");
            }
            line(code, "          FrontierExecutionOperations.applyUpdates(");
            line(code, "              _cf$state, waitResult.payload(), ALLOWED_FIELDS);");
            line(code, "          _cf$writes.addAll(waitResult.payload().keySet());");
            line(code, "        } else if (boundaryCompletion instanceof BoundaryCompletion.WaitExpired) {");
            if (await.timeout() == null) {
                line(code, "          throw new IllegalArgumentException(\"Wait does not declare a timeout\");");
            }
            line(code, "        } else {");
            line(code, "          throw new IllegalArgumentException(\"AFTER_WAIT requires a typed Wait result\");");
            line(code, "        }");
        } else if (descriptor.boundaryKind() == BoundaryKind.TIMER) {
            line(code, "        if (!(boundaryCompletion instanceof BoundaryCompletion.TimerFired)) {");
            line(code, "          throw new IllegalArgumentException(\"AFTER_TIMER requires TimerFired\");");
            line(code, "        }");
        } else if (descriptor.boundaryKind() == BoundaryKind.EFFECT) {
            line(code, "        if (!(boundaryCompletion instanceof BoundaryCompletion.EffectSucceeded effectResult)) {");
            line(code, "          throw new IllegalArgumentException(\"AFTER_EFFECT requires EffectSucceeded\");");
            line(code, "        }");
            line(code, "        FrontierExecutionOperations.applyUpdates(");
            line(code, "            _cf$state, effectResult.output(), ALLOWED_FIELDS);");
            line(code, "        _cf$writes.addAll(effectResult.output().keySet());");
        } else {
            line(code,
                    "        if (!(boundaryCompletion instanceof BoundaryCompletion.ProcessReturned processResult)) {");
            line(code, "          throw new IllegalArgumentException(\"PROCESS_CALL requires ProcessReturned\");");
            line(code, "        }");
            line(code, "        Map<String, Object> _cf$processCallUpdates = context.mapProcessCallOutput(");
            line(code, "            " + literal(boundaryId) + ", processResult.output());");
            line(code, "        FrontierExecutionOperations.applyUpdates(");
            line(code, "            _cf$state, _cf$processCallUpdates, ALLOWED_FIELDS);");
            line(code, "        _cf$writes.addAll(_cf$processCallUpdates.keySet());");
        }
        line(code, "        return " + nextExpression(machinePlan.nextAfterBoundary(boundaryId)) + ";");
        line(code, "      }");
    }

    private void generateFrameValidation(StringBuilder code, ResumeDescriptor descriptor) {
        List<ResumeDescriptor.FrameDescriptor> expected = descriptor.expectedFramePath();
        line(code, "        if (_cf$frames.size() != " + expected.size() + ") {");
        line(code, "          throw new IllegalArgumentException(\"Scope-frame depth does not match ResumePoint\");");
        line(code, "        }");
        for (int index = 0; index < expected.size(); index++) {
            ResumeDescriptor.FrameDescriptor frame = expected.get(index);
            String variable = "_cf$frame" + index;
            line(code, "        ScopeFrame " + variable + " = _cf$frames.get(" + index + ");");
            line(code, "        if (!" + literal(frame.loopId()) + ".equals(" + variable + ".loopId())) {");
            line(code, "          throw new IllegalArgumentException(");
            line(code, "              \"Scope-frame loop does not match ResumePoint\");");
            line(code, "        }");
            if (frame.kind() == ResumeDescriptor.FrameKind.FOR_EACH) {
                line(code, "        if (!(" + variable + " instanceof ForEachFrame)) {");
                line(code,
                        "          throw new IllegalArgumentException(\"Scope-frame kind does not match ResumePoint\");");
                line(code, "        }");
            } else if (frame.kind() == ResumeDescriptor.FrameKind.PARALLEL_FOR_EACH) {
                line(code, "        if (!(" + variable + " instanceof ParallelForEachFrame)) {");
                line(code,
                        "          throw new IllegalArgumentException(\"Scope-frame kind does not match ResumePoint\");");
                line(code, "        }");
            } else {
                if (frame.maxIterations() == null) {
                    line(code, "        if (!(" + variable + " instanceof WhileFrame)) {");
                } else {
                    line(code, "        if (!(" + variable + " instanceof WhileFrame _cf$while" + index + ")");
                    line(code, "            || _cf$while" + index + ".position() >= " + frame.maxIterations() + ") {");
                }
                line(code, "          throw new IllegalArgumentException(");
                line(code, "              \"While scope does not match ResumePoint\");");
                line(code, "        }");
            }
        }
    }

    private void generateFrontierCursor(StringBuilder code) {
        line(code, "");
        line(code, "  private static final class _cf$FrontierCursor {");
        line(code, "    private String nodeId;");
        line(code, "");
        line(code, "    private _cf$FrontierCursor(String nodeId) {");
        line(code, "      this.nodeId = nodeId;");
        line(code, "    }");
        line(code, "  }");
    }

    private void generateStepChunkLookup(StringBuilder code,
            List<List<Map.Entry<String, DurableMachinePlan.Step>>> nodeChunks) {
        List<List<String>> chunkIds =
                nodeChunks
            .stream()
            .map(chunk -> chunk.stream().map(Map.Entry::getKey).toList())
            .toList();
        generateChunkLookup(code, "_cf$stepChunk", "_cf$nodeId", chunkIds, "node");
    }

    private void generateResumeChunkLookup(StringBuilder code,
            List<List<Map.Entry<String, ResumeDescriptor>>> resumeChunks) {
        List<List<String>> chunkIds =
                resumeChunks
            .stream()
            .map(chunk -> chunk.stream().map(Map.Entry::getKey).toList())
            .toList();
        generateChunkLookup(code, "_cf$resumeChunk", "_cf$resumePoint", chunkIds, "ResumePoint");
    }

    private void generateChunkLookup(StringBuilder code, String methodName, String idName, List<List<String>> chunks,
            String entityName) {
        line(code, "");
        line(code, "  private int " + methodName + "(String " + idName + ") {");
        for (int index = 0; index < chunks.size() - 1; index++) {
            String lastId = chunks.get(index).get(chunks.get(index).size() - 1);
            line(code, "    if (" + idName + ".compareTo(" + literal(lastId) + ") <= 0) {");
            line(code, "      return " + methodName + "Lookup" + index + "(" + idName + ");");
            line(code, "    }");
        }
        int lastIndex = chunks.size() - 1;
        line(code, "    return " + methodName + "Lookup" + lastIndex + "(" + idName + ");");
        line(code, "  }");
        for (int index = 0; index < chunks.size(); index++) {
            line(code, "");
            line(code, "  private int " + methodName + "Lookup" + index + "(String " + idName + ") {");
            line(code, "    return switch (" + idName + ") {");
            for (String id : chunks.get(index)) {
                line(code, "      case " + literal(id) + " -> " + index + ";");
            }
            line(code,
                    "      default -> throw new IllegalArgumentException(\"Unknown " + entityName + ": \" + " + idName + ");");
            line(code, "    };");
            line(code, "  }");
        }
    }

    private void generateStepChunk(StringBuilder code, int chunkIndex,
            List<Map.Entry<String, DurableMachinePlan.Step>> nodesInChunk) {
        line(code, "");
        line(code, "  private FrontierStepResult _cf$stepChunk" + chunkIndex + "(");
        line(code, "      _cf$FrontierCursor _cf$cursor,");
        line(code, "      Map<String, Object> _cf$state,");
        line(code, "      List<ScopeFrame> _cf$frames,");
        line(code, "      Set<String> _cf$writes,");
        line(code, "      String _cf$activeJoin,");
        line(code, "      DurableExecutionContext context) throws Exception {");
        line(code, "    switch (_cf$cursor.nodeId) {");
        for (Map.Entry<String, DurableMachinePlan.Step> entry : nodesInChunk) {
            generateNodeCase(code, entry.getKey(), entry.getValue(), "_cf$cursor.nodeId");
        }
        line(code, "      default:");
        line(code, "        throw new IllegalArgumentException(\"Unknown node: \" + _cf$cursor.nodeId);");
        line(code, "    }");
        line(code, "  }");
    }

    private void generateNodeCase(StringBuilder code, String nodeId, DurableMachinePlan.Step step, String nodeIdTarget) {
        line(code, "        // Node: " + safeComment(nodeId) + " (" + step.getClass().getSimpleName() + ")");
        line(code, "        case " + literal(nodeId) + ": {");
        if (machinePlan.isConcurrentJoin(nodeId)) {
            line(code, "          if (" + literal(nodeId) + ".equals(_cf$activeJoin)) {");
            line(code, "            return new FrontierStepResult.AtJoin(");
            line(code, "                " + literal(nodeId) + ", _cf$state, _cf$frames);");
            line(code, "          }");
        }
        if (step instanceof DurableMachinePlan.Step.Advance advance) {
            generateNext(code, nodeIdTarget, advance.next());
        } else if (step instanceof DurableMachinePlan.Step.Complete) {
            line(code, "          if (!_cf$frames.isEmpty()) {");
            line(code,
                    "            throw new IllegalArgumentException(\"Root end reached with active control frames\");");
            line(code, "          }");
            String output = returnVariables().isEmpty() ? "output()" : "output(_cf$state)";
            line(code, "          return new FrontierStepResult.Completed(" + output + ", _cf$state);");
        } else if (step instanceof DurableMachinePlan.Step.Replayable replayable) {
            generateBoundActionInvocation(code, nodeId, "          ");
            generateNext(code, nodeIdTarget, replayable.next());
        } else if (step instanceof DurableMachinePlan.Step.Await) {
            generateWait(code, nodeId);
        } else if (step instanceof DurableMachinePlan.Step.Timer) {
            generateTimer(code, nodeId);
        } else if (step instanceof DurableMachinePlan.Step.Effect) {
            generateEffect(code, nodeId);
        } else if (step instanceof DurableMachinePlan.Step.ProcessCall) {
            generateProcessCall(code, nodeId);
        } else if (step instanceof DurableMachinePlan.Step.ChooseOne choose) {
            generateDecision(code, nodeId, choose, nodeIdTarget);
        } else if (step instanceof DurableMachinePlan.Step.ForkAll fork) {
            line(code, "          return new FrontierStepResult.Forked(");
            line(code, "              " + literal(nodeId) + ", " + literal(fork.joinNodeId()) + ",");
            line(code, "              " + branchActivations(fork.activations()) + ", _cf$state, _cf$frames);");
        } else if (step instanceof DurableMachinePlan.Step.ForkSelected fork) {
            generateInclusive(code, nodeId, fork);
        } else if (step instanceof DurableMachinePlan.Step.EnterIteration enter) {
            generateIterationEntry(code, enter.iterationId(), nodeIdTarget);
        } else if (step instanceof DurableMachinePlan.Step.BreakIteration control) {
            generateLoopControl(code, nodeId, control.iterationId(), control.condition(), control.whenNotTaken(), true,
                    nodeIdTarget);
        } else if (step instanceof DurableMachinePlan.Step.ContinueIteration control) {
            generateLoopControl(code, nodeId, control.iterationId(), control.condition(), control.whenNotTaken(), false,
                    nodeIdTarget);
        } else {
            throw new IllegalStateException("Unsupported Durable Machine step " + step.getClass().getName());
        }
        line(code, "        }");
    }

    private void generateWait(StringBuilder code, String nodeId) {
        AwaitPlan await = (AwaitPlan) semanticPlan.requireNode(nodeId).operation();
        line(code, "          return new FrontierStepResult.Waiting(");
        line(code,
                "              new WaitRequest(" + literal(nodeId) + ", " + literalOrNull(await.event()) + ", "
                + durationLiteralOrNull(await.timeout()) + ", context.describeWait(");
        line(code,
                "                  " + literal(nodeId) + ", FrontierExecutionOperations.readOnlyState(_cf$state), _cf$frames)),");
        line(code, "              SemanticCheckpoint.afterElement(" + literal(nodeId) + ", _cf$frames), _cf$state);");
    }

    private void generateTimer(StringBuilder code, String nodeId) {
        TimerPlan timer = (TimerPlan) semanticPlan.requireNode(nodeId).operation();
        String request = switch (timer.kind()) {
            case DURATION_LITERAL -> "TimerRequest.after(" + literal(nodeId) + ", "
                    + durationLiteral(timer.value(), nodeId) + ")";
            case DURATION_EXPRESSION -> "TimerRequest.after(" + literal(nodeId) + ", "
                    + expressionInvocation(conditionKey(nodeId, "timer")) + ")";
            case WAKE_AT_LITERAL -> "TimerRequest.at(" + literal(nodeId) + ", " + instantLiteral(timer.value(), nodeId)
                    + ")";
            case WAKE_AT_EXPRESSION -> "TimerRequest.at(" + literal(nodeId) + ", "
                    + expressionInvocation(conditionKey(nodeId, "timer")) + ")";
        };
        line(code, "          return new FrontierStepResult.TimerWaiting(");
        line(code, "              " + request + ",");
        line(code, "              SemanticCheckpoint.afterElement(" + literal(nodeId) + ", _cf$frames), _cf$state);");
    }

    private void generateEffect(StringBuilder code, String nodeId) {
        line(code, "          return new FrontierStepResult.EffectWaiting(");
        line(code, "              context.materializeEffectRequest(" + literal(nodeId) + ",");
        line(code, "                  FrontierExecutionOperations.readOnlyState(_cf$state), _cf$lexical(_cf$frames)),");
        line(code, "              SemanticCheckpoint.afterElement(" + literal(nodeId) + ", _cf$frames), _cf$state);");
    }

    private void generateProcessCall(StringBuilder code, String nodeId) {
        line(code, "          return new FrontierStepResult.ProcessCallRequested(");
        line(code, "              context.materializeProcessCallRequest(" + literal(nodeId) + ",");
        line(code, "                  FrontierExecutionOperations.readOnlyState(_cf$state), _cf$lexical(_cf$frames)),");
        line(code, "              SemanticCheckpoint.afterElement(" + literal(nodeId) + ", _cf$frames), _cf$state);");
    }

    private void generateInclusive(StringBuilder code, String nodeId, DurableMachinePlan.Step.ForkSelected fork) {
        line(code, "          List<BranchActivation> selected = new ArrayList<>();");
        for (int index = 0; index < fork.branches().size(); index++) {
            DurableMachinePlan.ConditionalBranch branch = fork.branches().get(index);
            line(code, "          if (" + expressionInvocation(transitionConditionKey(nodeId, index)) + ") {");
            line(code,
                    "            selected.add(new BranchActivation(" + branch.ordinal() + ", " + literal(branch.targetNodeId()) + "));");
            line(code, "          }");
        }
        line(code, "          if (selected.isEmpty()) {");
        if (fork.defaultActivation() == null) {
            line(code,
                    "            throw new IllegalStateException(" + literal("No inclusive branch matched at " + nodeId) + ");");
        } else {
            line(code,
                    "            selected.add(new BranchActivation(" + fork.defaultActivation().ordinal() + ", "
                    + literal(fork.defaultActivation().branchStartId()) + "));");
        }
        line(code, "          }");
        line(code, "          return new FrontierStepResult.Forked(");
        line(code,
                "              " + literal(nodeId) + ", " + literal(fork.joinNodeId()) + ", selected, _cf$state, _cf$frames);");
    }

    private void generateIterationEntry(StringBuilder code, String loopId, String nodeIdTarget) {
        DurableMachinePlan.Iteration iteration = machinePlan.requireIteration(loopId);
        if (iterationBody(iteration) instanceof DurableMachinePlan.Body.Operation) {
            line(code, "          if (!_cf$frames.isEmpty()");
            line(code,
                    "              && " + literal(loopId) + ".equals(_cf$frames.get(_cf$frames.size() - 1).loopId())) {");
            generateIterationOperation(code, loopId, nodeIdTarget, "            ");
            line(code, "          }");
        }
        if (iteration instanceof DurableMachinePlan.Iteration.ForEach loop) {
            line(code, "          List<Object> snapshot = FrontierExecutionOperations.snapshot(");
            line(code,
                    "              FrontierExecutionOperations.loopCollection(_cf$state, _cf$lexical(_cf$frames), "
                    + literal(loop.collectionVariable()) + ", " + literal(loopId) + "), " + literal(loopId) + ");");
            line(code, "          if (snapshot.isEmpty()) {");
            if (loop.outputTargetVariable() != null) {
                line(code, "            _cf$state.put(" + literal(loop.outputTargetVariable()) + ", List.of());");
                line(code, "            _cf$writes.add(" + literal(loop.outputTargetVariable()) + ");");
            }
            line(code, "            " + nodeIdTarget + " = " + nextExpression(loop.exit()) + ";");
            line(code, "          } else {");
            if (loop.execution() == IterationPlan.Execution.PARALLEL) {
                line(code, "            return new FrontierStepResult.ForkedEach(");
                line(code, "                " + literal(loopId) + ", snapshot, _cf$state, _cf$frames);");
            } else {
                line(code, "            _cf$frames.add(new ForEachFrame(" + literal(loopId) + ", 0, snapshot));");
                generateSequentialOutputReset(code, loop, "            ");
                line(code, "            " + nodeIdTarget + " = " + literal(bodyStart(loopId, loop.body())) + ";");
            }
            line(code, "          }");
        } else if (iteration instanceof DurableMachinePlan.Iteration.While loop) {
            if (loop.timing() == IterationPlan.ConditionTiming.BEFORE) {
                line(code, "          if (" + expressionInvocation(conditionKey(loopId, "iteration")) + ") {");
                line(code, "            _cf$frames.add(new WhileFrame(" + literal(loopId) + ", 0));");
                line(code, "            " + nodeIdTarget + " = " + literal(bodyStart(loopId, loop.body())) + ";");
                line(code, "          } else {");
                line(code, "            " + nodeIdTarget + " = " + nextExpression(loop.exit()) + ";");
                line(code, "          }");
            } else {
                line(code, "          _cf$frames.add(new WhileFrame(" + literal(loopId) + ", 0));");
                line(code, "          " + nodeIdTarget + " = " + literal(bodyStart(loopId, loop.body())) + ";");
            }
        }
        line(code, "          return null;");
    }

    private void generateIterationOperation(StringBuilder code, String loopId, String nodeIdTarget, String indent) {
        var operation = semanticPlan.requireNode(loopId).operation();
        if (operation instanceof ActionPlan action && action.execution() == ActionExecution.REPLAYABLE) {
            generateBoundActionInvocation(code, loopId, indent);
            line(code,
                    indent + nodeIdTarget + " = " + nextExpression(new DurableMachinePlan.Next.AdvanceIteration(loopId)) + ";");
            line(code, indent + "return null;");
        } else if (operation instanceof AwaitPlan) {
            generateWait(code, loopId);
        } else if (operation instanceof TimerPlan) {
            generateTimer(code, loopId);
        } else if (operation instanceof ActionPlan) {
            generateEffect(code, loopId);
        } else if (operation instanceof ProcessCallPlan) {
            generateProcessCall(code, loopId);
        } else {
            throw new IllegalStateException("Unsupported iteration operation at " + loopId);
        }
    }

    private void generateLoopControl(StringBuilder code, String nodeId, String loopId, BoundExpression condition,
            DurableMachinePlan.Next whenNotTaken, boolean breakControl, String nodeIdTarget) {
        String control = breakControl ? "breakLoop" : "advanceLoop";
        String controlInvocation = breakControl
                ? control + "(" + literal(loopId) + ", _cf$state, _cf$frames, _cf$writes)"
                : advanceLoopInvocation(loopId);
        if (condition == null) {
            line(code, "          " + nodeIdTarget + " = " + controlInvocation + ";");
        } else {
            line(code, "          if (" + expressionInvocation(conditionKey(nodeId, "control")) + ") {");
            line(code, "            " + nodeIdTarget + " = " + controlInvocation + ";");
            line(code, "          } else {");
            line(code, "            " + nodeIdTarget + " = " + nextExpression(whenNotTaken) + ";");
            line(code, "          }");
        }
        line(code, "          return null;");
    }

    private void generateDecision(StringBuilder code, String nodeId, DurableMachinePlan.Step.ChooseOne decision,
            String nodeIdTarget) {
        for (int index = 0; index < decision.branches().size(); index++) {
            DurableMachinePlan.ConditionalBranch branch = decision.branches().get(index);
            line(code, "          if (" + expressionInvocation(transitionConditionKey(nodeId, index)) + ") {");
            line(code, "            " + nodeIdTarget + " = " + literal(branch.targetNodeId()) + ";");
            line(code, "            return null;");
            line(code, "          }");
        }
        if (decision.defaultTargetNodeId() != null) {
            line(code, "          " + nodeIdTarget + " = " + literal(decision.defaultTargetNodeId()) + ";");
            line(code, "          return null;");
            return;
        }
        line(code,
                "          throw new IllegalStateException(" + literal("No decision branch matched at " + nodeId) + ");");
    }

    private void generateLoopAdvance(StringBuilder code) {
        line(code, "");
        line(code, "  private String advanceLoop(");
        line(code, "      String loopId, Map<String, Object> _cf$state,");
        line(code, "      List<ScopeFrame> _cf$frames, Set<String> _cf$writes) {");
        line(code, "    switch (loopId) {");
        for (Map.Entry<String, DurableMachinePlan.Iteration> entry : sortedByKey(machinePlan.iterations())) {
            String loopId = entry.getKey();
            DurableMachinePlan.Iteration iteration = entry.getValue();
            line(code, "      case " + literal(loopId) + ": {");
            if (iteration instanceof DurableMachinePlan.Iteration.ForEach loop) {
                if (loop.execution() == IterationPlan.Execution.PARALLEL) {
                    line(code, "        ParallelForEachFrame frame = (ParallelForEachFrame)");
                    line(code, "            FrontierExecutionOperations.requireTopFrame(_cf$frames, loopId);");
                    line(code, "        return \"\\u0000parallel-end:\" + loopId;");
                    line(code, "      }");
                    continue;
                }
                line(code, "        ForEachFrame frame = (ForEachFrame)");
                line(code, "            FrontierExecutionOperations.requireTopFrame(_cf$frames, loopId);");
                if (loop.outputTargetVariable() != null) {
                    line(code, "        frame = frame.recordResult(");
                    line(code, "            _cf$state.get(" + literal(loop.outputSourceVariable()) + "));");
                }
                line(code, "        int next = frame.position() + 1;");
                line(code, "        if (next < frame.snapshotSize()) {");
                line(code, "          FrontierExecutionOperations.replaceTopFrame(_cf$frames, loopId,");
                line(code, "              frame.advance());");
                generateSequentialOutputReset(code, loop, "          ");
                line(code, "          return " + literal(bodyStart(loopId, loop.body())) + ";");
                line(code, "        }");
                line(code, "        FrontierExecutionOperations.popTopFrame(_cf$frames, loopId);");
                generateSequentialResultPublication(code, loop, "frame", "        ");
                line(code, "        return " + nextExpression(loop.exit()) + ";");
            } else if (iteration instanceof DurableMachinePlan.Iteration.While loop) {
                String condition = expressionInvocation(conditionKey(loopId, "iteration"));
                line(code, "        WhileFrame frame = (WhileFrame)");
                line(code, "            FrontierExecutionOperations.requireTopFrame(_cf$frames, loopId);");
                line(code, "        int next = frame.position() + 1;");
                line(code, "        FrontierExecutionOperations.replaceTopFrame(_cf$frames, loopId,");
                line(code, "            new WhileFrame(loopId, next));");
                line(code, "        if (" + condition + ") {");
                if (loop.maxIterations() != null) {
                    line(code, "          if (next >= " + loop.maxIterations() + ") {");
                    if (loop.limitBehavior() == IterationPlan.LimitBehavior.FAIL) {
                        line(code, "            return FrontierStepResult.WHILE_FAILURE_PREFIX + loopId;");
                    } else {
                        line(code, "            FrontierExecutionOperations.popTopFrame(_cf$frames, loopId);");
                        line(code, "            return " + nextExpression(loop.exit()) + ";");
                    }
                    line(code, "          }");
                }
                line(code, "          return " + literal(bodyStart(loopId, loop.body())) + ";");
                line(code, "        }");
                line(code, "        FrontierExecutionOperations.popTopFrame(_cf$frames, loopId);");
                line(code, "        return " + nextExpression(loop.exit()) + ";");
            }
            line(code, "      }");
        }
        line(code, "      default:");
        line(code, "        throw new IllegalArgumentException(\"Unknown loop: \" + loopId);");
        line(code, "    }");
        line(code, "  }");
    }

    private void generateLoopBreak(StringBuilder code) {
        line(code, "");
        line(code, "  private String breakLoop(");
        line(code, "      String loopId, Map<String, Object> _cf$state,");
        line(code, "      List<ScopeFrame> _cf$frames, Set<String> _cf$writes) {");
        line(code, "    switch (loopId) {");
        for (Map.Entry<String, DurableMachinePlan.Iteration> entry : sortedByKey(machinePlan.iterations())) {
            String loopId = entry.getKey();
            DurableMachinePlan.Iteration iteration = entry.getValue();
            line(code, "      case " + literal(loopId) + ": {");
            if (iteration instanceof DurableMachinePlan.Iteration.ForEach loop
                    && loop.execution() == IterationPlan.Execution.SEQUENTIAL && loop.outputTargetVariable() != null) {
                line(code, "        ForEachFrame frame = (ForEachFrame)");
                line(code, "            FrontierExecutionOperations.requireTopFrame(_cf$frames, loopId);");
                line(code, "        frame = frame.recordResult(");
                line(code, "            _cf$state.get(" + literal(loop.outputSourceVariable()) + "));");
                line(code, "        FrontierExecutionOperations.popTopFrame(_cf$frames, loopId);");
                generateSequentialResultPublication(code, loop, "frame", "        ");
            } else {
                line(code, "        FrontierExecutionOperations.popTopFrame(_cf$frames, loopId);");
            }
            line(code, "        return " + nextExpression(iterationExit(iteration)) + ";");
            line(code, "      }");
        }
        line(code, "      default:");
        line(code, "        throw new IllegalArgumentException(\"Unknown loop: \" + loopId);");
        line(code, "    }");
        line(code, "  }");
    }

    private void generateSequentialResultPublication(StringBuilder code, DurableMachinePlan.Iteration.ForEach loop,
            String frameVariable, String indent) {
        if (loop.outputTargetVariable() == null) {
            return;
        }
        line(code,
                indent + "_cf$state.put(" + literal(loop.outputTargetVariable()) + ", " + frameVariable + ".completedResults());");
        line(code, indent + "_cf$writes.add(" + literal(loop.outputTargetVariable()) + ");");
    }

    private void generateSequentialOutputReset(StringBuilder code, DurableMachinePlan.Iteration.ForEach loop,
            String indent) {
        if (loop.outputSourceVariable() == null) {
            return;
        }
        line(code,
                indent + "_cf$state.put(" + literal(loop.outputSourceVariable()) + ", " + outputSourceInitialValue(loop) + ");");
        line(code, indent + "_cf$writes.add(" + literal(loop.outputSourceVariable()) + ");");
    }

    private String outputSourceInitializer(DurableMachinePlan.Iteration.ForEach loop) {
        return loop.outputSourceVariable() == null ? "null" : "() -> " + outputSourceInitialValue(loop);
    }

    private String outputSourceInitialValue(DurableMachinePlan.Iteration.ForEach loop) {
        ProcessSemanticPlan.VariablePlan output =
                machinePlan.semanticPlan().requireVariable(loop.outputSourceVariable());
        return DataTypes
            .generateDefaultValueCode(DataTypes.getJavaClass(output.dataType(), classLoader), output.defaultValue())
            .expression();
    }

    private void generateLexicalBindings(StringBuilder code) {
        line(code, "");
        line(code, "  private Map<String, Object> _cf$lexical(");
        line(code, "      List<ScopeFrame> _cf$frames) {");
        line(code, "    Map<String, Object> result = new LinkedHashMap<>();");
        line(code, "    for (ScopeFrame controlFrame : _cf$frames) {");
        line(code, "      switch (controlFrame.loopId()) {");
        for (Map.Entry<String, DurableMachinePlan.Iteration> entry : sortedByKey(machinePlan.iterations())) {
            String loopId = entry.getKey();
            DurableMachinePlan.Iteration iteration = entry.getValue();
            line(code, "        case " + literal(loopId) + ": {");
            if (iteration instanceof DurableMachinePlan.Iteration.ForEach loop) {
                String frameType =
                        loop.execution() == IterationPlan.Execution.PARALLEL ? "ParallelForEachFrame" : "ForEachFrame";
                line(code, "          " + frameType + " frame = (" + frameType + ") controlFrame;");
                line(code, "          result.put(" + literal(loop.itemVariable()) + ", frame.currentValue());");
                if (loop.indexVariable() != null) {
                    line(code, "          result.put(" + literal(loop.indexVariable()) + ", frame.position());");
                }
            } else if (iteration instanceof DurableMachinePlan.Iteration.While loop && loop.indexVariable() != null) {
                line(code, "          result.put(" + literal(loop.indexVariable()) + ", controlFrame.position());");
            }
            line(code, "          break;");
            line(code, "        }");
        }
        line(code, "        default:");
        line(code, "          throw new IllegalArgumentException(");
        line(code, "              \"Unknown control frame: \" + controlFrame.loopId());");
        line(code, "      }");
        line(code, "    }");
        line(code, "    return FrontierExecutionOperations.immutableLexical(result);");
        line(code, "  }");
    }

    private void generateOutput(StringBuilder code) {
        line(code, "");
        List<ProcessSemanticPlan.VariablePlan> returnVariables = returnVariables();
        if (returnVariables.isEmpty()) {
            line(code, "  private Map<String, Object> output() {");
            line(code, "    return Map.of();");
            line(code, "  }");
            return;
        }
        line(code, "  private Map<String, Object> output(");
        line(code, "      Map<String, Object> _cf$state) {");
        line(code, "    Map<String, Object> result = new LinkedHashMap<>();");
        for (ProcessSemanticPlan.VariablePlan variable : returnVariables) {
            line(code,
                    "    result.put(" + literal(variable.name()) + ", _cf$state.get(" + literal(variable.name()) + "));");
        }
        line(code, "    return result;");
        line(code, "  }");
    }

    private List<ProcessSemanticPlan.VariablePlan> returnVariables() {
        return sortedValues(stateVariables)
            .stream()
            .filter(variable -> variable.role() == ProcessSemanticPlan.VariableRole.RETURN)
            .toList();
    }

    private void generateExpressionMethods(StringBuilder code) {
        for (ExpressionMethod method : sortedValues(expressionMethods)) {
            BoundExpression expression = method.expression();
            line(code, "");
            generateBoundMethodDeclaration(code, expressionResultType(expression.resultKind()), method.methodName(),
                    expression.bindings());
            if (usesLexicalBindings(expression.bindings())) {
                line(code, "    Map<String, Object> _cf$lexical = _cf$lexical(_cf$frames);");
            }
            for (BoundExpression.Binding variable : expression.bindings()) {
                String source = variable.source() == BoundExpression.Binding.Source.STATE
                        ? "_cf$state.get(" + literal(variable.name()) + ")"
                        : "_cf$lexical.get(" + literal(variable.name()) + ")";
                String typeName = shortTypeName(variable.typeName());
                line(code, "    " + typeName + " " + variable.name() + " = (" + typeName + ") " + source + ";");
            }
            if (expression.resultKind() == BoundExpression.ResultKind.BOOLEAN) {
                line(code, "    return ConditionSemantics.isTrue(");
                line(code, "        " + expression.source() + ");");
            } else {
                line(code, "    return (" + expressionResultType(expression.resultKind()) + ") (");
                line(code, "        " + expression.source() + ");");
            }
            line(code, "  }");
        }
    }

    /**
     * Compiles an ISO-8601 duration literal into an equivalent {@link Duration} factory call
     * so the generated source never relies on runtime {@code Duration.parse}. Parsing happens
     * at compile time so an invalid literal fails the build instead of a future Run.
     */
    private String durationLiteral(String value, String methodName) {
        Duration parsed;
        try {
            parsed = Duration.parse(value);
        } catch (DateTimeParseException failure) {
            throw new IllegalStateException("Timer " + methodName + " has an invalid ISO-8601 duration: " + value,
                    failure);
        }
        return durationLiteral(parsed);
    }

    private static String durationLiteralOrNull(Duration duration) {
        return duration == null ? "null" : durationLiteral(duration);
    }

    private static String durationLiteral(Duration parsed) {
        long seconds = parsed.getSeconds();
        int nano = parsed.getNano();
        if (nano == 0) {
            return "Duration.ofSeconds(" + seconds + "L)";
        }
        return "Duration.ofSeconds(" + seconds + "L, " + nano + ")";
    }

    private String instantLiteral(String value, String methodName) {
        try {
            Instant parsed = Instant.parse(value);
            return "Instant.parse(" + literal(parsed.toString()) + ")";
        } catch (DateTimeParseException failure) {
            throw new IllegalStateException("Timer " + methodName + " has an invalid ISO-8601 instant: " + value,
                    failure);
        }
    }

    private void generateBoundActionInvocation(StringBuilder code, String elementId, String indent) {
        ActionPlan action = (ActionPlan) semanticPlan.requireNode(elementId).operation();
        line(code, indent + "FrontierExecutionOperations.applyUpdates(_cf$state,");
        line(code, indent + "    context.invokeReplayable(");
        line(code, indent + "        " + literal(elementId) + ",");
        line(code, indent + "        FrontierExecutionOperations.readOnlyState(_cf$state),");
        line(code, indent + "        _cf$lexical(_cf$frames)),");
        line(code, indent + "    ALLOWED_FIELDS);");
        if (action.output() != null) {
            line(code, indent + "_cf$writes.add(" + literal(action.output().target()) + ");");
        }
    }

    private void generateNext(StringBuilder code, String target, DurableMachinePlan.Next next) {
        line(code, "          " + target + " = " + nextExpression(next) + ";");
        line(code, "          return null;");
    }

    private String nextExpression(DurableMachinePlan.Next next) {
        return nextExpression(next, "_cf$state", "_cf$frames", "_cf$writes");
    }

    private String nextExpression(DurableMachinePlan.Next next, String stateVariable, String framesVariable,
            String writesVariable) {
        if (next instanceof DurableMachinePlan.Next.Node node) {
            return literal(node.nodeId());
        }
        DurableMachinePlan.Next.AdvanceIteration iteration = (DurableMachinePlan.Next.AdvanceIteration) next;
        return advanceLoopInvocation(iteration.iterationId(), stateVariable, framesVariable, writesVariable);
    }

    private void collectExpressions() {
        for (Map.Entry<String, DurableMachinePlan.Step> entry : sortedByKey(machinePlan.steps())) {
            String nodeId = entry.getKey();
            DurableMachinePlan.Step step = entry.getValue();
            if (step instanceof DurableMachinePlan.Step.ChooseOne choose) {
                addBranchExpressions(nodeId, choose.branches());
            } else if (step instanceof DurableMachinePlan.Step.ForkSelected fork) {
                addBranchExpressions(nodeId, fork.branches());
            } else if (step instanceof DurableMachinePlan.Step.BreakIteration control) {
                addExpression(conditionKey(nodeId, "control"), control.condition());
            } else if (step instanceof DurableMachinePlan.Step.ContinueIteration control) {
                addExpression(conditionKey(nodeId, "control"), control.condition());
            } else if (step instanceof DurableMachinePlan.Step.Timer timer) {
                addExpression(conditionKey(nodeId, "timer"), timer.scheduleExpression());
            }
        }
        for (Map.Entry<String, DurableMachinePlan.Iteration> entry : sortedByKey(machinePlan.iterations())) {
            if (entry.getValue() instanceof DurableMachinePlan.Iteration.While loop) {
                addExpression(conditionKey(entry.getKey(), "iteration"), loop.condition());
            }
            DurableMachinePlan.Body body = iterationBody(entry.getValue());
            if (body instanceof DurableMachinePlan.Body.Operation operation) {
                addExpression(conditionKey(entry.getKey(), "timer"), operation.scheduleExpression());
            }
        }
    }

    private void addBranchExpressions(String nodeId, List<DurableMachinePlan.ConditionalBranch> branches) {
        for (int index = 0; index < branches.size(); index++) {
            addExpression(transitionConditionKey(nodeId, index), branches.get(index).condition());
        }
    }

    private void addExpression(String key, BoundExpression expression) {
        if (expression != null) {
            expressionMethods.put(key, new ExpressionMethod(semanticMethodName("expression", key), expression));
        }
    }

    private void collectGeneratedTypes() {
        addGeneratedTypes(BoundaryCompletion.class, BranchActivation.class, BranchFrame.class,
                ConcurrentBranchFrame.class, ConcurrentFrontierOperations.class, ContinuationSnapshot.class,
                FrontierId.class, FrontierSnapshot.class, ScopeFrame.class, DurableProgram.class,
                DurableExecutionContext.class, FrontierExecutionOperations.class, MachineTurnResult.class,
                MachineTurnScheduler.class, MultiInstanceFrontierOperations.class, OccurrenceKey.class,
                OccurrenceResult.class, ResumePoint.class, ResumeDescriptor.class, SemanticCheckpoint.class,
                FrontierStepResult.class, TurnBudget.class, Generated.class, ArrayList.class, LinkedHashMap.class,
                LinkedHashSet.class, List.class, Map.class, Objects.class, Set.class);
        if (semanticPlan
            .getNodes()
            .values()
            .stream()
            .anyMatch(node -> node.operation() instanceof AwaitPlan)) {
            addGeneratedType(WaitRequest.class);
        }
        if (semanticPlan
            .getNodes()
            .values()
            .stream()
            .map(ProcessSemanticPlan.NodePlan::operation)
            .anyMatch(operation -> operation instanceof AwaitPlan await && await.timeout() != null
                    || operation instanceof TimerPlan timer && timer.kind() != TimerPlan.Kind.WAKE_AT_EXPRESSION)) {
            addGeneratedType(Duration.class);
        }
        if (semanticPlan
            .getNodes()
            .values()
            .stream()
            .anyMatch(node -> node.operation() instanceof TimerPlan)) {
            addGeneratedType(TimerRequest.class);
        }
        if (expressionMethods
            .values()
            .stream()
            .anyMatch(spec -> spec.expression().resultKind() == BoundExpression.ResultKind.BOOLEAN)) {
            addGeneratedType(ConditionSemantics.class);
        }
        for (DurableMachinePlan.Iteration iteration : sortedValues(machinePlan.iterations())) {
            if (iteration instanceof DurableMachinePlan.Iteration.While) {
                addGeneratedType(WhileFrame.class);
            } else if (((DurableMachinePlan.Iteration.ForEach) iteration).execution() == IterationPlan.Execution.PARALLEL) {
                addGeneratedType(ParallelForEachFrame.class);
            } else {
                addGeneratedType(ForEachFrame.class);
            }
        }
        for (ExpressionMethod expression : sortedValues(expressionMethods)) {
            if (expression.expression().resultKind() == BoundExpression.ResultKind.INSTANT) {
                addGeneratedType(Instant.class);
            } else if (expression.expression().resultKind() == BoundExpression.ResultKind.DURATION) {
                addGeneratedType(Duration.class);
            }
            addBindingTypes(expression.expression().bindings());
        }
        if (semanticPlan
            .getNodes()
            .values()
            .stream()
            .map(ProcessSemanticPlan.NodePlan::operation)
            .anyMatch(operation -> operation instanceof TimerPlan timer && timer.kind() == TimerPlan.Kind.WAKE_AT_LITERAL)) {
            addGeneratedType(Instant.class);
        }
        for (ProcessSemanticPlan.VariablePlan variable : stateVariables.values()) {
            DataTypes.DefaultValueCode defaultValue = DataTypes.generateDefaultValueCode(DataTypes.getJavaClass(variable.dataType(),
                            classLoader), variable.defaultValue());
            defaultValue.referencedTypes().forEach(this::addGeneratedType);
        }
    }

    private void addBindingTypes(List<BoundExpression.Binding> bindings) {
        bindings.forEach(binding -> addGeneratedType(JavaTypeName.of(binding.typeName())));
    }

    private void addGeneratedTypes(Class<?>... types) {
        for (Class<?> type : types) {
            addGeneratedType(type);
        }
    }

    private void addGeneratedType(Class<?> type) {
        addGeneratedType(JavaTypeName.of(type));
    }

    private void addGeneratedType(JavaTypeName type) {
        for (JavaTypeName referencedType : type.getReferencedTypes()) {
            addSingleGeneratedType(referencedType);
        }
    }

    private void addSingleGeneratedType(JavaTypeName type) {
        String importName = type.getImportName();
        if (importName == null) {
            return;
        }
        if (type.getSimpleName().equals(className) && !importName.equals(getClassFullName())) {
            throw new IllegalArgumentException(
                    "Generated class name '" + className + "' conflicts with referenced type " + importName);
        }
        JavaTypeName existing = referencedTypesBySimpleName.putIfAbsent(type.getSimpleName(), type);
        if (existing != null && !existing.getImportName().equals(importName)) {
            throw new IllegalArgumentException(
                    "Conflicting Durable generated-code type references for '" + type.getSimpleName() + "': "
                    + existing.getImportName() + " and " + importName);
        }
        if ("java.lang".equals(type.getPackageName()) || GENERATED_PACKAGE.equals(type.getPackageName())) {
            return;
        }
        importedTypes.putIfAbsent(importName, type);
    }

    private String expressionInvocation(String key) {
        ExpressionMethod expression = expressionMethods.get(key);
        if (expression == null) {
            throw new IllegalArgumentException("Missing generated expression for " + key);
        }
        return boundMethodInvocation(expression.methodName(), expression.expression().bindings());
    }

    private String boundMethodInvocation(String methodName, List<BoundExpression.Binding> bindings) {
        List<String> arguments = new ArrayList<>(2);
        if (usesStateBindings(bindings)) {
            arguments.add("_cf$state");
        }
        if (usesLexicalBindings(bindings)) {
            arguments.add("_cf$frames");
        }
        return methodName + "(" + String.join(", ", arguments) + ")";
    }

    private void generateBoundMethodDeclaration(StringBuilder code, String returnType, String methodName,
            List<BoundExpression.Binding> bindings) {
        boolean usesState = usesStateBindings(bindings);
        boolean usesLexical = usesLexicalBindings(bindings);
        if (!usesState && !usesLexical) {
            line(code, "  private " + returnType + " " + methodName + "() {");
            return;
        }
        line(code, "  private " + returnType + " " + methodName + "(");
        if (usesState) {
            line(code, "      Map<String, Object> _cf$state" + (usesLexical ? "," : ") {"));
        }
        if (usesLexical) {
            line(code, "      List<ScopeFrame> _cf$frames) {");
        }
    }

    private static String expressionResultType(BoundExpression.ResultKind kind) {
        return switch (kind) {
            case BOOLEAN -> "boolean";
            case DURATION -> "Duration";
            case INSTANT -> "Instant";
        };
    }

    private boolean hasBreakControl() {
        return machinePlan
            .steps()
            .values()
            .stream()
            .anyMatch(DurableMachinePlan.Step.BreakIteration.class::isInstance);
    }

    private boolean requiresLexicalBindings() {
        return !machinePlan.iterations().isEmpty()
                || semanticPlan
                    .getNodes()
                    .values()
                    .stream()
                    .anyMatch(node -> node.operation() instanceof ActionPlan
                            || node.operation() instanceof ProcessCallPlan)
                || expressionMethods
                    .values()
                    .stream()
                    .anyMatch(expression -> usesLexicalBindings(expression.expression().bindings()));
    }

    private String advanceLoopInvocation(String loopId) {
        return advanceLoopInvocation(loopId, "_cf$state", "_cf$frames", "_cf$writes");
    }

    private String advanceLoopInvocation(String loopId, String stateVariable, String framesVariable,
            String writesVariable) {
        return "advanceLoop(" + literal(loopId) + ", " + stateVariable + ", " + framesVariable + ", " + writesVariable
                + ")";
    }

    private String bodyStart(String iterationId, DurableMachinePlan.Body body) {
        return body instanceof DurableMachinePlan.Body.Scope scope ? scope.startNodeId() : iterationId;
    }

    private static DurableMachinePlan.Body iterationBody(DurableMachinePlan.Iteration iteration) {
        return iteration instanceof DurableMachinePlan.Iteration.While loop
                ? loop.body()
                : ((DurableMachinePlan.Iteration.ForEach) iteration).body();
    }

    private static DurableMachinePlan.Next iterationExit(DurableMachinePlan.Iteration iteration) {
        return iteration instanceof DurableMachinePlan.Iteration.While loop
                ? loop.exit()
                : ((DurableMachinePlan.Iteration.ForEach) iteration).exit();
    }

    private record ExpressionMethod(String methodName, BoundExpression expression) {}
}
