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
package com.alibaba.compileflow.engine.core.semantic.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.core.model.action.ActionExecution;
import com.alibaba.compileflow.engine.core.model.action.EffectRecovery;
import com.alibaba.compileflow.engine.core.model.action.EffectiveInvocationPolicy;
import com.alibaba.compileflow.engine.core.model.action.RetryJitter;
import com.alibaba.compileflow.engine.core.semantic.CanonicalEncoding;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProcessSemanticPlanTest {
    @Test
    void digestRetainsVariableAndTransitionOrderButIgnoresNodeMapOrder() {
        ProcessSemanticPlan first = plan(List.of("left", "right"), false, false);
        ProcessSemanticPlan reorderedVariables = plan(List.of("left", "right"), true, false);
        ProcessSemanticPlan reorderedNodes = plan(List.of("left", "right"), false, true);
        ProcessSemanticPlan reorderedTransitions = plan(List.of("right", "left"), false, false);

        assertThat(reorderedVariables.getDigest()).isNotEqualTo(first.getDigest());
        assertThat(reorderedNodes.canonicalForm()).isEqualTo(first.canonicalForm());
        assertThat(reorderedNodes.getDigest()).isEqualTo(first.getDigest());
        assertThat(reorderedTransitions.getDigest()).isNotEqualTo(first.getDigest());
        assertThat(reorderedVariables.getVariables().keySet()).containsExactly("result", "value");
        assertThat(reorderedNodes.getNodes().keySet()).containsExactly("right", "left", "start");
        assertThat(first.getVariables().keySet()).containsExactly("value", "result");
        assertThat(first.getNodes().keySet()).containsExactly("start", "left", "right");
    }

    @Test
    void derivesScopeOwnershipAndLexicalIterationBindings() {
        Map<String, ProcessSemanticPlan.VariablePlan> variables = Map.of("items",
                new ProcessSemanticPlan.VariablePlan("items", "java.util.List<java.lang.String>",
                        ProcessSemanticPlan.VariableRole.PARAM, null), "result", variable("result"));
        ProcessSemanticPlan.NodePlan loop = new ProcessSemanticPlan.NodePlan("loop",
                ProcessSemanticPlan.NodeKind.ACTIVITY, ProcessSemanticPlan.ROOT_SCOPE_ID,
                new ProcessSemanticPlan.ScopeBoundary("inner", "inner"), List.of(), null, null,
                new IterationPlan.ForEach("items", "item", String.class.getName(), "index",
                        IterationPlan.Execution.SEQUENTIAL, null, null));
        ProcessSemanticPlan.NodePlan inner = new ProcessSemanticPlan.NodePlan("inner",
                ProcessSemanticPlan.NodeKind.ACTIVITY, "loop", null, List.of(), null, null, null);
        ProcessSemanticPlan plan =
                new ProcessSemanticPlan("scope.test", variables, Map.of("loop", loop, "inner", inner));

        assertThat(plan.ownsScope("loop")).isTrue();
        assertThat(plan.parentScope("loop")).isEqualTo(ProcessSemanticPlan.ROOT_SCOPE_ID);
        assertThat(plan.nodesInScope("loop")).extracting(ProcessSemanticPlan.NodePlan::id).containsExactly("inner");
        assertThat(plan.visibleVariables("inner"))
            .containsEntry("item",
                    new ProcessSemanticPlan.VisibleVariable("item", String.class.getName(),
                            ProcessSemanticPlan.VisibleVariable.Origin.ITERATION))
            .containsEntry("index",
                    new ProcessSemanticPlan.VisibleVariable("index", "int",
                            ProcessSemanticPlan.VisibleVariable.Origin.ITERATION));
    }

    @Test
    void exposesAnActivityOwnIterationBindingsOnlyInsideItsBody() {
        IterationPlan.ForEach iteration = new IterationPlan.ForEach("items", "item", String.class.getName(), "index",
                IterationPlan.Execution.SEQUENTIAL, null, null);
        ActionPlan operation = new ActionPlan(ActionExecution.REPLAYABLE,
                new ActionInvocation.Java("example.Action", "execute"),
                List.of(ActionPlan.Input.expression("item", "value", String.class.getName())), null,
                EffectiveInvocationPolicy.defaults(), null);
        ProcessSemanticPlan.NodePlan activity = new ProcessSemanticPlan.NodePlan("activity",
                ProcessSemanticPlan.NodeKind.ACTIVITY, ProcessSemanticPlan.ROOT_SCOPE_ID, null, List.of(), null,
                operation, iteration);

        ProcessSemanticPlan plan =
                new ProcessSemanticPlan("own.iteration.bindings", semanticVariables(), Map.of("activity", activity));

        assertThat(plan.visibleVariables("activity"))
            .containsEntry("item",
                    new ProcessSemanticPlan.VisibleVariable("item", String.class.getName(),
                            ProcessSemanticPlan.VisibleVariable.Origin.ITERATION))
            .containsEntry("index",
                    new ProcessSemanticPlan.VisibleVariable("index", "int",
                            ProcessSemanticPlan.VisibleVariable.Origin.ITERATION));

        ProcessSemanticPlan.NodePlan selfReferentialCollection = new ProcessSemanticPlan.NodePlan("activity",
                ProcessSemanticPlan.NodeKind.ACTIVITY, ProcessSemanticPlan.ROOT_SCOPE_ID, null, List.of(), null, null,
                new IterationPlan.ForEach("item", "item", String.class.getName(), null,
                        IterationPlan.Execution.SEQUENTIAL, null, null));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new ProcessSemanticPlan("self.referential.collection", Map.of(),
                    Map.of("activity", selfReferentialCollection)))
            .withMessageContaining("outside its visible scope");
    }

    @Test
    void enforcesLexicalCollisionsByScopeInsteadOfGlobally() {
        ProcessSemanticPlan.NodePlan firstLoop = scopeLoop("firstLoop", "firstBody", "item");
        ProcessSemanticPlan.NodePlan firstBody = new ProcessSemanticPlan.NodePlan("firstBody",
                ProcessSemanticPlan.NodeKind.ACTIVITY, "firstLoop", null, List.of(), null, null, null);
        ProcessSemanticPlan.NodePlan secondLoop = scopeLoop("secondLoop", "secondBody", "item");
        ProcessSemanticPlan.NodePlan secondBody = new ProcessSemanticPlan.NodePlan("secondBody",
                ProcessSemanticPlan.NodeKind.ACTIVITY, "secondLoop", null, List.of(), null, null, null);

        ProcessSemanticPlan siblings = new ProcessSemanticPlan("sibling.lexical.names", semanticVariables(),
                Map.of("firstLoop", firstLoop, "firstBody", firstBody, "secondLoop", secondLoop, "secondBody",
                        secondBody));

        assertThat(siblings.visibleVariables("firstBody")).containsKey("item");
        assertThat(siblings.visibleVariables("secondBody")).containsKey("item");

        ProcessSemanticPlan.NodePlan shadowingProcessVariable = scopeLoop("loop", "body", "result");
        ProcessSemanticPlan.NodePlan body = new ProcessSemanticPlan.NodePlan("body",
                ProcessSemanticPlan.NodeKind.ACTIVITY, "loop", null, List.of(), null, null, null);
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new ProcessSemanticPlan("process.shadow", semanticVariables(),
                    Map.of("loop", shadowingProcessVariable, "body", body)))
            .withMessageContaining("shadows a visible variable");

        ProcessSemanticPlan.NodePlan nestedLoop = new ProcessSemanticPlan.NodePlan("nested",
                ProcessSemanticPlan.NodeKind.ACTIVITY, "firstLoop", null, List.of(), null, null, forEach("item"));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new ProcessSemanticPlan("lexical.shadow", semanticVariables(),
                    Map.of("firstLoop", firstLoop, "firstBody", firstBody, "nested", nestedLoop)))
            .withMessageContaining("shadows a visible variable");
    }

    @Test
    void validatesActionInvocationIdentityAndPreservesScriptSource() {
        ActionInvocation.Script script = new ActionInvocation.Script("qlexpress", "  value + 1  ");

        assertThat(script.language()).isEqualTo("qlexpress");
        assertThat(script.source()).isEqualTo("  value + 1  ");
        assertThatThrownBy(() -> new ActionInvocation.Script(" qlexpress ", "value + 1"))
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("surrounding whitespace");
        assertThatThrownBy(() -> new ActionInvocation.Script("QLEXPRESS", "value + 1"))
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("lowercase kebab-case");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new ActionInvocation.SpringBean("orders", null, "execute"))
            .withMessageContaining("declaredClass");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new ActionInvocation.Java("example.Action", "not-valid"))
            .withMessageContaining("valid Java identifier");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new ActionInvocation.Script("qlexpress", "   "))
            .withMessageContaining("source must not be blank");
    }

    @Test
    void retainsIrreducibleNestedScopeBoundariesInTheDigest() {
        ProcessSemanticPlan first = scopedPlan(new ProcessSemanticPlan.ScopeBoundary("first", "first"));
        ProcessSemanticPlan second = scopedPlan(new ProcessSemanticPlan.ScopeBoundary("second", "second"));

        assertThat(first.requireNode("scope").scopeBoundary())
            .isEqualTo(new ProcessSemanticPlan.ScopeBoundary("first", "first"));
        assertThat(second.getDigest()).isNotEqualTo(first.getDigest());
    }

    @Test
    void rejectsMissingOrForeignNestedScopeBoundaries() {
        ProcessSemanticPlan.NodePlan ownerWithoutBoundary = new ProcessSemanticPlan.NodePlan("scope",
                ProcessSemanticPlan.NodeKind.ACTIVITY, ProcessSemanticPlan.ROOT_SCOPE_ID, null, List.of(), null, null,
                null);
        ProcessSemanticPlan.NodePlan child = new ProcessSemanticPlan.NodePlan("child",
                ProcessSemanticPlan.NodeKind.ACTIVITY, "scope", null, List.of(), null, null, null);

        assertThatIllegalArgumentException()
            .isThrownBy(() -> new ProcessSemanticPlan("missing.boundary", Map.of(),
                    Map.of("scope", ownerWithoutBoundary, "child", child)))
            .withMessageContaining("owns a nested scope but declares no boundary");

        ProcessSemanticPlan.NodePlan ownerWithForeignBoundary = new ProcessSemanticPlan.NodePlan("scope",
                ProcessSemanticPlan.NodeKind.ACTIVITY, ProcessSemanticPlan.ROOT_SCOPE_ID,
                new ProcessSemanticPlan.ScopeBoundary("foreign", "child"), List.of(), null, null, null);
        ProcessSemanticPlan.NodePlan foreign = new ProcessSemanticPlan.NodePlan("foreign",
                ProcessSemanticPlan.NodeKind.ACTIVITY, ProcessSemanticPlan.ROOT_SCOPE_ID, null, List.of(), null, null,
                null);
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new ProcessSemanticPlan("foreign.boundary", Map.of(),
                    Map.of("scope", ownerWithForeignBoundary, "child", child, "foreign", foreign)))
            .withMessageContaining("outside its owned scope");
    }

    @Test
    void rejectsInvalidControlScopeAndVariableReferences() {
        ProcessSemanticPlan.NodePlan action = new ProcessSemanticPlan.NodePlan("task",
                ProcessSemanticPlan.NodeKind.ACTIVITY, ProcessSemanticPlan.ROOT_SCOPE_ID, null, List.of(), null,
                new ActionPlan(null, new ActionInvocation.Java("example.Action", "execute"),
                        List.of(ActionPlan.Input.expression("missing", "value", String.class.getName())), null,
                        EffectiveInvocationPolicy.defaults(), null), null);

        assertThatIllegalArgumentException()
            .isThrownBy(() -> new ProcessSemanticPlan("invalid.mapping", Map.of(), Map.of("task", action)))
            .withMessageContaining("outside its visible scope");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new ProcessSemanticPlan.NodePlan("start", ProcessSemanticPlan.NodeKind.START,
                    ProcessSemanticPlan.ROOT_SCOPE_ID, null, List.of(), "true", null, null))
            .withMessageContaining("controlCondition");

        ProcessSemanticPlan.NodePlan a = new ProcessSemanticPlan.NodePlan("a", ProcessSemanticPlan.NodeKind.ACTIVITY,
                "b", null, List.of(), null, null, null);
        ProcessSemanticPlan.NodePlan b = new ProcessSemanticPlan.NodePlan("b", ProcessSemanticPlan.NodeKind.ACTIVITY,
                "a", null, List.of(), null, null, null);
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new ProcessSemanticPlan("invalid.scope", Map.of(), Map.of("a", a, "b", b)))
            .withMessageContaining("contains a cycle");
    }

    @Test
    void acceptsLiteralActionInputsWithoutAStateBinding() {
        ProcessSemanticPlan.NodePlan action = new ProcessSemanticPlan.NodePlan("task",
                ProcessSemanticPlan.NodeKind.ACTIVITY, ProcessSemanticPlan.ROOT_SCOPE_ID, null, List.of(), null,
                new ActionPlan(null, new ActionInvocation.Java("example.Action", "execute"),
                        List.of(ActionPlan.Input.literal("literal", "value", String.class.getName())), null,
                        EffectiveInvocationPolicy.defaults(), null), null);

        assertThat(new ProcessSemanticPlan("literal.input", Map.of(), Map.of("task", action)).requireNode("task"))
            .isEqualTo(action);
    }

    @Test
    void acceptsExpressionActionInputsBoundToVisibleState() {
        ProcessSemanticPlan.NodePlan action = new ProcessSemanticPlan.NodePlan("task",
                ProcessSemanticPlan.NodeKind.ACTIVITY, ProcessSemanticPlan.ROOT_SCOPE_ID, null, List.of(), null,
                new ActionPlan(null, new ActionInvocation.Java("example.Action", "execute"),
                        List.of(ActionPlan.Input.expression("input.value", "value", String.class.getName())), null,
                        EffectiveInvocationPolicy.defaults(), null), null);

        assertThat(new ProcessSemanticPlan("expression.input", Map.of("input", variable("input")),
                Map.of("task", action))
            .requireNode("task"))
            .isEqualTo(action);
    }

    @Test
    void preservesEmptyAndWhitespaceLiteralDefaultsExactly() {
        ProcessSemanticPlan.VariablePlan emptyVariable = new ProcessSemanticPlan.VariablePlan("empty",
                String.class.getName(), ProcessSemanticPlan.VariableRole.INNER, "");
        ProcessSemanticPlan.VariablePlan whitespaceVariable = new ProcessSemanticPlan.VariablePlan("whitespace",
                String.class.getName(), ProcessSemanticPlan.VariableRole.INNER, "  ");
        ActionPlan.Input actionInput = ActionPlan.Input.literal("  ", "value", String.class.getName());
        ProcessCallPlan.Input childInput = new ProcessCallPlan.Input(null, "value", "");

        assertThat(emptyVariable.defaultValue()).isEmpty();
        assertThat(whitespaceVariable.defaultValue()).isEqualTo("  ");
        assertThat(((ActionPlan.InputSource.Literal) actionInput.source()).value()).isEqualTo("  ");
        assertThat(childInput.defaultValue()).isEmpty();
    }

    @Test
    void rejectsBlankOptionalExpressionsInsteadOfChangingTheirMeaning() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new ProcessSemanticPlan.TransitionPlan("end", "  ", false))
            .withMessage("condition must not be blank");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new ProcessSemanticPlan.NodePlan("break", ProcessSemanticPlan.NodeKind.BREAK,
                    ProcessSemanticPlan.ROOT_SCOPE_ID, null, List.of(), "  ", null, null))
            .withMessage("controlCondition must not be blank");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> ProcessCallTarget.from("", null))
            .withMessageContaining("resourcePath must not be blank");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new ProcessSemanticPlan.TransitionPlan("end", "\u00a0", false))
            .withMessage("condition must not be blank");
        assertThat(new ProcessSemanticPlan.TransitionPlan("end", "  ready  ", false).condition()).isEqualTo("  ready  ");
    }

    @Test
    void processCallClasspathRequiresOneCanonicalRootLocation() {
        assertThat(new ProcessCallTarget.Classpath("flows/child.bpm").resourcePath()).isEqualTo("flows/child.bpm");
        for (String invalid :
                List.of("/flows/child.bpm", "./child.bpm", "../child.bpm", "flows//child.bpm", "flows/./child.bpm",
                        "flows/*/child.bpm", "classpath:/flows/child.bpm", "file:/flows/child.bpm", "flows\\child.bpm")) {
            assertThatIllegalArgumentException()
                .as(invalid)
                .isThrownBy(() -> new ProcessCallTarget.Classpath(invalid));
        }
    }

    @Test
    void canonicalEncodingDistinguishesNullFromEveryLiteral() {
        ProcessSemanticPlan absent = new ProcessSemanticPlan("null.encoding",
                Map.of("value",
                        new ProcessSemanticPlan.VariablePlan("value", String.class.getName(),
                                ProcessSemanticPlan.VariableRole.INNER, null)), Map.of());
        ProcessSemanticPlan literal = new ProcessSemanticPlan("null.encoding",
                Map.of("value",
                        new ProcessSemanticPlan.VariablePlan("value", String.class.getName(),
                                ProcessSemanticPlan.VariableRole.INNER, "<null>")), Map.of());

        assertThat(absent.canonicalForm()).isNotEqualTo(literal.canonicalForm());
        assertThat(absent.getDigest()).isNotEqualTo(literal.getDigest());
        assertThatIllegalArgumentException()
            .isThrownBy(() -> CanonicalEncoding.sha256("malformed\ud800"))
            .withMessageContaining("valid Unicode");
    }

    @Test
    void nodeIdentityBoundsUnicodeCodePointsWithoutNormalizing() {
        String maximum = "\ud83d\ude00".repeat(512);

        assertThat(new ProcessSemanticPlan.NodePlan(maximum, ProcessSemanticPlan.NodeKind.END,
                ProcessSemanticPlan.ROOT_SCOPE_ID, null, List.of(), null, null, null)
            .id())
            .isEqualTo(maximum);
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new ProcessSemanticPlan.NodePlan("\ud83d\ude00".repeat(513),
                    ProcessSemanticPlan.NodeKind.END, ProcessSemanticPlan.ROOT_SCOPE_ID, null, List.of(), null, null,
                    null))
            .withMessageContaining("512 characters");
    }

    @Test
    void timerPlanDistinguishesLiteralIdentityFromExactExpressionText() {
        assertThat(new TimerPlan(TimerPlan.Kind.DURATION_EXPRESSION, "  delay  ").value()).isEqualTo("  delay  ");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new TimerPlan(TimerPlan.Kind.DURATION_LITERAL, " PT1S"))
            .withMessageContaining("surrounding whitespace");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new TimerPlan(TimerPlan.Kind.WAKE_AT_EXPRESSION, "\u00a0"))
            .withMessage("value must not be blank");
    }

    @Test
    void actionPlanRejectsIncompleteEffectSemantics() {
        ActionInvocation invocation = new ActionInvocation.Java("example.Action", "execute");

        assertThatIllegalArgumentException()
            .isThrownBy(() -> new ActionPlan(ActionExecution.EFFECT, invocation, List.of(), null,
                    EffectiveInvocationPolicy.defaults(), null))
            .withMessageContaining("requires exactly one resolved effectPolicy");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new ActionPlan(ActionExecution.REPLAYABLE, invocation, List.of(), null,
                    EffectiveInvocationPolicy.defaults(),
                    new EffectPolicyPlan(null, EffectRecovery.MANUAL, 1, 0, null, null, null)))
            .withMessageContaining("requires exactly one resolved effectPolicy");
    }

    @Test
    void digestIncludesEveryBehaviorChangingSemanticField() {
        assertThat(defaultFlowPlan(false).getDigest()).isNotEqualTo(defaultFlowPlan(true).getDigest());
        assertThat(controlPlan("count > 1").getDigest()).isNotEqualTo(controlPlan("count > 2").getDigest());
        assertThat(operationPlan(new AwaitPlan("approved", Duration.ofMinutes(1))).getDigest())
            .isNotEqualTo(operationPlan(new AwaitPlan("approved", Duration.ofMinutes(2))).getDigest());
        assertThat(operationPlan(action(EffectiveInvocationPolicy.defaults(), null)).getDigest())
            .isNotEqualTo(operationPlan(
                    action(EffectiveInvocationPolicy.of(0L, 500L, 2, 100L, 2.0d, 1_000L, RetryJitter.NONE, "transient",
                                    "propagate"), null))
                .getDigest());
        assertThat(operationPlan(
                action(EffectiveInvocationPolicy.defaults(),
                        new EffectPolicyPlan(null, EffectRecovery.MANUAL, 1, 0, null, null, null)))
            .getDigest())
            .isNotEqualTo(operationPlan(
                    action(EffectiveInvocationPolicy.defaults(),
                            new EffectPolicyPlan(null, EffectRecovery.RETRY, 3, 0, Duration.ofSeconds(1), null, null)))
                .getDigest());
        assertThat(iterationPlan(
                new IterationPlan.While("count < 3", IterationPlan.ConditionTiming.BEFORE, 3,
                        IterationPlan.LimitBehavior.FAIL, "index"))
            .getDigest())
            .isNotEqualTo(iterationPlan(
                    new IterationPlan.While("count < 3", IterationPlan.ConditionTiming.AFTER, 3,
                            IterationPlan.LimitBehavior.FAIL, "index"))
                .getDigest());
        assertThat(iterationPlan(
                new IterationPlan.ForEach("items", "item", String.class.getName(), "index",
                        IterationPlan.Execution.SEQUENTIAL, "elementOutput", "outputs"))
            .getDigest())
            .isNotEqualTo(iterationPlan(
                    new IterationPlan.ForEach("items", "item", String.class.getName(), "index",
                            IterationPlan.Execution.PARALLEL, "elementOutput", "outputs"))
                .getDigest());
    }

    @Test
    void effectPolicyPlanRejectsStatesOutsideProtocolBounds() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new EffectPolicyPlan(null, EffectRecovery.RETRY, 101, 0, Duration.ofSeconds(1), null, null))
            .withMessageContaining("maxAttempts must not exceed 100");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new EffectPolicyPlan(null, EffectRecovery.RECONCILE, 1, 1001, Duration.ofSeconds(1), null,
                    null))
            .withMessageContaining("maxReconcileAttempts must not exceed 1000");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new EffectPolicyPlan(null, EffectRecovery.RETRY, 2, 0, Duration.ofNanos(1), null, null))
            .withMessageContaining("whole-millisecond precision");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new EffectPolicyPlan(null, EffectRecovery.RETRY, 2, 0, Duration.ofDays(1).plusMillis(1),
                    null, null))
            .withMessageContaining("recoveryDelay must be positive and at most PT24H");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new EffectPolicyPlan(null, EffectRecovery.RETRY, 2, 0, Duration.ofSeconds(1),
                    Duration.ofDays(30).plusMillis(1), null))
            .withMessageContaining("maxRecoveryDuration must be positive and at most PT720H");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new EffectPolicyPlan("class", null, 0, 0, null, null, null))
            .withMessageContaining("recoveryPlanVariable must be a variable name");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new ReconcilePlan(new ActionInvocation.Java("example.Action", "reconcile"),
                    List.of(ReconcilePlan.Input.requestField("request", "value", String.class.getName()),
                            ReconcilePlan.Input.requestField("request", "value", String.class.getName()))))
            .withMessageContaining("duplicate targets");
    }

    @Test
    void rejectsUnknownParallelIterationOutputSourceVariable() {
        IterationPlan.ForEach iteration = new IterationPlan.ForEach("items", "item", String.class.getName(), "index",
                IterationPlan.Execution.PARALLEL, "missing", "outputs");

        assertThatIllegalArgumentException()
            .isThrownBy(() -> iterationPlan(iteration))
            .withMessageContaining("Unknown Process variable 'missing'");
    }

    @Test
    void validatesForEachCollectionAndElementTypes() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> forEachPlan("java.lang.String", "java.lang.String", null, null, null))
            .withMessageContaining("must be an Iterable or array");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> forEachPlan("java.util.List<java.lang.Integer>", "java.lang.String", null, null, null))
            .withMessageContaining("incompatible with collection type");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> forEachPlan("java.lang.Integer[]", "java.lang.String", null, null, null))
            .withMessageContaining("incompatible with array type");

        assertThat(forEachPlan("java.util.List", "java.lang.String", null, null, null)).isNotNull();
        assertThat(forEachPlan("java.lang.String[]", "java.lang.CharSequence", null, null, null)).isNotNull();
    }

    @Test
    void validatesForEachOutputTypes() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> forEachPlan("java.util.List<java.lang.String>", "java.lang.String",
                    "java.util.Set<java.lang.String>", "java.lang.String", ProcessSemanticPlan.VariableRole.INNER))
            .withMessageContaining("must declare java.util.List");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> forEachPlan("java.util.List<java.lang.String>", "java.lang.String",
                    "java.util.List<java.lang.Integer>", "java.lang.String", ProcessSemanticPlan.VariableRole.INNER))
            .withMessageContaining("must contain output source type");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> forEachPlan("java.util.List<java.lang.String>", "java.lang.String",
                    "java.util.List<java.lang.String>", "java.lang.String", ProcessSemanticPlan.VariableRole.RETURN))
            .withMessageContaining("must be an inner process variable");
        ProcessSemanticPlan.VariablePlan sharedOutput = new ProcessSemanticPlan.VariablePlan("output", "java.util.List",
                ProcessSemanticPlan.VariableRole.INNER, null);
        IterationPlan.ForEach sharedOutputIteration = new IterationPlan.ForEach("items", "item", "java.lang.String",
                null, IterationPlan.Execution.SEQUENTIAL, "output", "output");
        ProcessSemanticPlan.NodePlan sharedOutputActivity = new ProcessSemanticPlan.NodePlan("activity",
                ProcessSemanticPlan.NodeKind.ACTIVITY, ProcessSemanticPlan.ROOT_SCOPE_ID, null, List.of(), null, null,
                sharedOutputIteration);
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new ProcessSemanticPlan("for.each.shared.output",
                    Map.of("items",
                            new ProcessSemanticPlan.VariablePlan("items", "java.util.List<java.lang.String>",
                                    ProcessSemanticPlan.VariableRole.PARAM, null), "output", sharedOutput),
                    Map.of("activity", sharedOutputActivity)))
            .withMessageContaining("must be different");

        assertThat(
                forEachPlan("java.util.List<java.lang.String>", "java.lang.String", "java.util.List", "java.lang.String",
                        ProcessSemanticPlan.VariableRole.INNER))
            .isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"java.util.ArrayList<java.lang.String>", "java.util.LinkedList<java.lang.String>"})
    void aggregationDoesNotPromiseAConcreteListImplementation(String targetType) {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> forEachPlan("java.util.List<java.lang.String>", "java.lang.String", targetType,
                    "java.lang.String", ProcessSemanticPlan.VariableRole.INNER))
            .withMessageContaining("must declare java.util.List");
    }

    private static ProcessSemanticPlan forEachPlan(String collectionType, String itemType, String outputTargetType,
            String outputSourceType, ProcessSemanticPlan.VariableRole outputSourceRole) {
        Map<String, ProcessSemanticPlan.VariablePlan> variables = new LinkedHashMap<>();
        variables.put("items",
                new ProcessSemanticPlan.VariablePlan("items", collectionType, ProcessSemanticPlan.VariableRole.PARAM,
                        null));
        if (outputTargetType != null) {
            variables.put("outputs",
                    new ProcessSemanticPlan.VariablePlan("outputs", outputTargetType,
                            ProcessSemanticPlan.VariableRole.RETURN, null));
            variables.put("elementOutput",
                    new ProcessSemanticPlan.VariablePlan("elementOutput", outputSourceType, outputSourceRole, null));
        }
        IterationPlan.ForEach iteration = new IterationPlan.ForEach("items", "item", itemType, null,
                IterationPlan.Execution.SEQUENTIAL, outputTargetType == null ? null : "elementOutput",
                outputTargetType == null ? null : "outputs");
        ProcessSemanticPlan.NodePlan activity = new ProcessSemanticPlan.NodePlan("activity",
                ProcessSemanticPlan.NodeKind.ACTIVITY, ProcessSemanticPlan.ROOT_SCOPE_ID, null, List.of(), null, null,
                iteration);
        return new ProcessSemanticPlan("for.each.types", variables, Map.of("activity", activity));
    }

    private static ProcessSemanticPlan defaultFlowPlan(boolean defaultFlow) {
        ProcessSemanticPlan.NodePlan choice = new ProcessSemanticPlan.NodePlan("choice",
                ProcessSemanticPlan.NodeKind.EXCLUSIVE_GATEWAY, ProcessSemanticPlan.ROOT_SCOPE_ID, null,
                List.of(new ProcessSemanticPlan.TransitionPlan("end", null, defaultFlow)), null, null, null);
        return new ProcessSemanticPlan("digest.default", Map.of(),
                Map.of("choice", choice, "end", node("end", ProcessSemanticPlan.NodeKind.END, List.of())));
    }

    private static ProcessSemanticPlan controlPlan(String condition) {
        ProcessSemanticPlan.NodePlan control = new ProcessSemanticPlan.NodePlan("break",
                ProcessSemanticPlan.NodeKind.BREAK, ProcessSemanticPlan.ROOT_SCOPE_ID, null, List.of(), condition, null,
                null);
        return new ProcessSemanticPlan("digest.control", Map.of("count", variable("count")), Map.of("break", control));
    }

    private static ProcessSemanticPlan operationPlan(OperationPlan operation) {
        ProcessSemanticPlan.NodePlan activity = new ProcessSemanticPlan.NodePlan("activity",
                ProcessSemanticPlan.NodeKind.ACTIVITY, ProcessSemanticPlan.ROOT_SCOPE_ID, null, List.of(), null,
                operation, null);
        return new ProcessSemanticPlan("digest.operation", semanticVariables(), Map.of("activity", activity));
    }

    private static ProcessSemanticPlan iterationPlan(IterationPlan iteration) {
        ProcessSemanticPlan.NodePlan activity = new ProcessSemanticPlan.NodePlan("activity",
                ProcessSemanticPlan.NodeKind.ACTIVITY, ProcessSemanticPlan.ROOT_SCOPE_ID, null, List.of(), null, null,
                iteration);
        return new ProcessSemanticPlan("digest.iteration", semanticVariables(), Map.of("activity", activity));
    }

    private static ActionPlan action(EffectiveInvocationPolicy invocationPolicy, EffectPolicyPlan effectPolicy) {
        return new ActionPlan(effectPolicy == null ? ActionExecution.REPLAYABLE : ActionExecution.EFFECT,
                new ActionInvocation.Java("example.Action", "execute"), List.of(),
                new ActionPlan.Output("result", String.class.getName(), String.class.getName()), invocationPolicy,
                effectPolicy);
    }

    private static Map<String, ProcessSemanticPlan.VariablePlan> semanticVariables() {
        return Map.of("count", variable("count"), "items",
                new ProcessSemanticPlan.VariablePlan("items", "java.util.List<java.lang.String>",
                        ProcessSemanticPlan.VariableRole.PARAM, null), "outputs",
                new ProcessSemanticPlan.VariablePlan("outputs", "java.util.List<java.lang.String>",
                        ProcessSemanticPlan.VariableRole.RETURN, null), "elementOutput", variable("elementOutput"),
                "result", variable("result"));
    }

    private static ProcessSemanticPlan plan(List<String> branches, boolean reverseVariables, boolean reverseNodes) {
        Map<String, ProcessSemanticPlan.VariablePlan> variables = new LinkedHashMap<>();
        variables.put("value", variable("value"));
        variables.put("result", variable("result"));
        Map<String, ProcessSemanticPlan.NodePlan> nodes = new LinkedHashMap<>();
        nodes.put("start", node("start", ProcessSemanticPlan.NodeKind.START, branches));
        nodes.put("left", node("left", ProcessSemanticPlan.NodeKind.END, List.of()));
        nodes.put("right", node("right", ProcessSemanticPlan.NodeKind.END, List.of()));
        if (reverseVariables) {
            Map<String, ProcessSemanticPlan.VariablePlan> reorderedVariables = new LinkedHashMap<>();
            reorderedVariables.put("result", variables.get("result"));
            reorderedVariables.put("value", variables.get("value"));
            variables = reorderedVariables;
        }
        if (reverseNodes) {
            Map<String, ProcessSemanticPlan.NodePlan> reorderedNodes = new LinkedHashMap<>();
            reorderedNodes.put("right", nodes.get("right"));
            reorderedNodes.put("left", nodes.get("left"));
            reorderedNodes.put("start", nodes.get("start"));
            nodes = reorderedNodes;
        }
        return new ProcessSemanticPlan("digest.test", variables, nodes);
    }

    private static ProcessSemanticPlan scopedPlan(ProcessSemanticPlan.ScopeBoundary boundary) {
        ProcessSemanticPlan.NodePlan owner = new ProcessSemanticPlan.NodePlan("scope",
                ProcessSemanticPlan.NodeKind.ACTIVITY, ProcessSemanticPlan.ROOT_SCOPE_ID, boundary, List.of(), null,
                null, null);
        ProcessSemanticPlan.NodePlan first = new ProcessSemanticPlan.NodePlan("first",
                ProcessSemanticPlan.NodeKind.ACTIVITY, "scope", null, List.of(), null, null, null);
        ProcessSemanticPlan.NodePlan second = new ProcessSemanticPlan.NodePlan("second",
                ProcessSemanticPlan.NodeKind.ACTIVITY, "scope", null, List.of(), null, null, null);
        return new ProcessSemanticPlan("scope.digest", Map.of(),
                Map.of("scope", owner, "first", first, "second", second));
    }

    private static ProcessSemanticPlan.NodePlan scopeLoop(String id, String bodyId, String itemVariable) {
        return new ProcessSemanticPlan.NodePlan(id, ProcessSemanticPlan.NodeKind.ACTIVITY,
                ProcessSemanticPlan.ROOT_SCOPE_ID, new ProcessSemanticPlan.ScopeBoundary(bodyId, bodyId), List.of(),
                null, null, forEach(itemVariable));
    }

    private static IterationPlan.ForEach forEach(String itemVariable) {
        return new IterationPlan.ForEach("items", itemVariable, String.class.getName(), "index",
                IterationPlan.Execution.SEQUENTIAL, null, null);
    }

    private static ProcessSemanticPlan.NodePlan node(String id, ProcessSemanticPlan.NodeKind kind,
            List<String> outgoingTargets) {
        return new ProcessSemanticPlan.NodePlan(id, kind, ProcessSemanticPlan.ROOT_SCOPE_ID, null,
                outgoingTargets
                    .stream()
                    .map(target -> new ProcessSemanticPlan.TransitionPlan(target, null, false))
                    .toList(), null, null, null);
    }

    private static ProcessSemanticPlan.VariablePlan variable(String name) {
        return new ProcessSemanticPlan.VariablePlan(name, String.class.getName(), ProcessSemanticPlan.VariableRole.INNER,
                null);
    }
}
