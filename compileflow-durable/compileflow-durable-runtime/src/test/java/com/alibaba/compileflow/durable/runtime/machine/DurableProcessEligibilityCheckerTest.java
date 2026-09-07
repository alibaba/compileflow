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

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.durable.runtime.kernel.ResumePoint;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionInvocation;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.AwaitPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.IterationPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.OperationPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.TimerPlan;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.tbbpm.semantic.TbbpmSemanticFrontend;
import com.alibaba.compileflow.engine.tbbpm.parser.TbbpmXmlParser;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModel;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DurableProcessEligibilityCheckerTest {
    private static final String GOLDEN_FIXTURE = "tbbpm-durable-v1/all-constructs.bpm";

    @Test
    void semanticVariantUniverseIsExplicitAndExhaustive() {
        assertThat(ProcessSemanticPlan.NodeKind.values())
            .containsExactlyInAnyOrder(ProcessSemanticPlan.NodeKind.START, ProcessSemanticPlan.NodeKind.END,
                    ProcessSemanticPlan.NodeKind.ACTIVITY, ProcessSemanticPlan.NodeKind.EXCLUSIVE_GATEWAY,
                    ProcessSemanticPlan.NodeKind.PARALLEL_GATEWAY, ProcessSemanticPlan.NodeKind.INCLUSIVE_GATEWAY,
                    ProcessSemanticPlan.NodeKind.BREAK, ProcessSemanticPlan.NodeKind.CONTINUE);
        assertThat(Set.of(OperationPlan.class.getPermittedSubclasses()))
            .containsExactlyInAnyOrder(ActionPlan.class, AwaitPlan.class, TimerPlan.class, ProcessCallPlan.class);
        assertThat(Set.of(IterationPlan.class.getPermittedSubclasses()))
            .containsExactlyInAnyOrder(IterationPlan.While.class, IterationPlan.ForEach.class);
        assertThat(Set.of(ActionInvocation.class.getPermittedSubclasses()))
            .containsExactlyInAnyOrder(ActionInvocation.Java.class, ActionInvocation.SpringBean.class,
                    ActionInvocation.Script.class);
    }

    @Test
    void completeTbbpmSourceFixtureLowersThroughTheSourceNeutralDurableTarget() throws IOException {
        TbbpmModel model = goldenFixture();
        ProcessSemanticPlan semantics = new TbbpmSemanticFrontend().compile(model);

        try (ScriptExecutorRegistry scripts = ScriptExecutorRegistry.builtIns(getClass().getClassLoader())) {
            DurableProcessCompiler compiler = new DurableProcessCompiler(scripts);
            assertThat(compiler.check(semantics).problems()).isEmpty();
            DurableMachinePlan plan = compiler.lower(semantics);
            List<String> resumeKeys = new java.util.ArrayList<>(plan
                .steps()
                .keySet()
                .stream()
                .map(nodeId -> ResumePoint.beforeElement(nodeId).key())
                .toList());
            resumeKeys.add(ResumePoint.afterElement("timer").key());
            resumeKeys.add(ResumePoint.afterElement("wait").key());
            resumeKeys.add(ResumePoint.afterElement("event").key());
            resumeKeys.add(ResumePoint.afterElement("child").key());
            resumeKeys.add(ResumePoint.atJoin("parallelJoin").key());
            resumeKeys.add(ResumePoint.atJoin("inclusiveJoin").key());
            assertThat(plan.resumes()).containsOnlyKeys(resumeKeys.toArray(String[]::new));
            assertThat(operationNodeIds(plan, TimerPlan.class)).containsExactly("timer");
            assertThat(operationNodeIds(plan, AwaitPlan.class)).containsExactlyInAnyOrder("wait", "event");
            assertThat(operationNodeIds(plan, ProcessCallPlan.class)).containsExactly("child");
        }
    }

    @Test
    void preparationValidatesAndBindsEachExpressionOnce() throws IOException {
        ProcessSemanticPlan semantics = new TbbpmSemanticFrontend().compile(goldenFixture());
        AtomicInteger validations = new AtomicInteger();
        DurableExpressionValidator validator =
                (expression, visibleTypes, targetType) -> {
            validations.incrementAndGet();
            return List.of();
        };
        try (ScriptExecutorRegistry scripts = ScriptExecutorRegistry.builtIns(getClass().getClassLoader())) {
            new DurableProcessCompiler(scripts, validator, true).lower(semantics);
        }

        assertThat(validations).hasValue(expressionCount(semantics));
    }

    @Test
    void acceptsAtPrefixedStringDefaultsAsLiterals() {
        TbbpmModel model = TbbpmXmlParser
            .getInstance()
            .parse(FlowSource.of("durable.literal.default",
                    """
            <bpm code="durable.literal.default">
              <var name="value" dataType="java.lang.String" inOutType="param" defaultValue="@name"/>
              <start id="start"><transition to="end"/></start>
              <end id="end"/>
            </bpm>
            """
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        ProcessSemanticPlan semantics = new TbbpmSemanticFrontend().compile(model);
        try (ScriptExecutorRegistry scripts = ScriptExecutorRegistry.builtIns(getClass().getClassLoader())) {
            assertThat(new DurableProcessCompiler(scripts).check(semantics).problems()).isEmpty();
        }
    }

    @Test
    void rejectsElementIdentitiesThatCannotBePersisted() {
        String oversizedId = "n".repeat(129);
        ProcessSemanticPlan.NodePlan start = new ProcessSemanticPlan.NodePlan(oversizedId,
                ProcessSemanticPlan.NodeKind.START, ProcessSemanticPlan.ROOT_SCOPE_ID, null,
                List.of(new ProcessSemanticPlan.TransitionPlan("end", null, false)), null, null, null);
        ProcessSemanticPlan.NodePlan end = new ProcessSemanticPlan.NodePlan("end", ProcessSemanticPlan.NodeKind.END,
                ProcessSemanticPlan.ROOT_SCOPE_ID, null, List.of(), null, null, null);
        ProcessSemanticPlan semantics =
                new ProcessSemanticPlan("durable.id.bound", Map.of(), Map.of(oversizedId, start, "end", end));
        try (ScriptExecutorRegistry scripts = ScriptExecutorRegistry.builtIns(getClass().getClassLoader())) {
            assertThat(new DurableProcessCompiler(scripts).check(semantics).problems())
                .extracting(DurableModelEligibility.Problem::code)
                .contains("DURABLE_ELEMENT_ID_TOO_LONG");

            String maximumId = "\ud83d\ude00".repeat(128);
            ProcessSemanticPlan.NodePlan unicodeStart = new ProcessSemanticPlan.NodePlan(maximumId,
                    ProcessSemanticPlan.NodeKind.START, ProcessSemanticPlan.ROOT_SCOPE_ID, null,
                    List.of(new ProcessSemanticPlan.TransitionPlan("end", null, false)), null, null, null);
            ProcessSemanticPlan unicodeSemantics =
                    new ProcessSemanticPlan("durable.unicode.id", Map.of(), Map.of(maximumId, unicodeStart, "end", end));

            assertThat(new DurableProcessCompiler(scripts).check(unicodeSemantics).problems())
                .extracting(DurableModelEligibility.Problem::code)
                .doesNotContain("DURABLE_ELEMENT_ID_TOO_LONG");
        }
    }

    private TbbpmModel goldenFixture() throws IOException {
        ClassLoader loader = getClass().getClassLoader();
        try (InputStream input = loader.getResourceAsStream(GOLDEN_FIXTURE)) {
            assertThat(input).as("golden fixture resource").isNotNull();
            byte[] bytes = Objects.requireNonNull(input).readAllBytes();
            return TbbpmXmlParser.getInstance().parse(FlowSource.of("durable.protocol.all-constructs", bytes));
        }
    }

    private static List<String> operationNodeIds(DurableMachinePlan plan, Class<?> operationType) {
        return plan
            .semanticPlan()
            .getNodes()
            .values()
            .stream()
            .filter(node -> operationType.isInstance(node.operation()))
            .map(com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan.NodePlan::id)
            .toList();
    }

    private static int expressionCount(ProcessSemanticPlan semantics) {
        int count = 0;
        for (ProcessSemanticPlan.NodePlan node : semantics.getNodes().values()) {
            count += node.controlCondition() == null ? 0 : 1;
            count += (int) node
                .outgoingTransitions()
                .stream()
                .filter(transition -> transition.condition() != null)
                .count();
            count += node.iteration() instanceof IterationPlan.While ? 1 : 0;
            count += node.operation() instanceof TimerPlan timer && !timer.isLiteral() ? 1 : 0;
        }
        return count;
    }
}
