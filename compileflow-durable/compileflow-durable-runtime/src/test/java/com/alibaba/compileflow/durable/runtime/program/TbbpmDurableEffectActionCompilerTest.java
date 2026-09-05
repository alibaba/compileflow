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

import static com.alibaba.compileflow.durable.runtime.kernel.DurableProgramTestSupport.advanceAfter;
import static com.alibaba.compileflow.durable.runtime.kernel.DurableProgramTestSupport.advanceStart;
import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.durable.api.effect.EffectRecoveryPlan;
import com.alibaba.compileflow.durable.runtime.machine.DurableMachinePlan;
import com.alibaba.compileflow.durable.runtime.kernel.BoundaryCompletion;
import com.alibaba.compileflow.durable.runtime.codec.DurableValueSerializer;
import com.alibaba.compileflow.durable.runtime.kernel.FrontierStepResult;
import com.alibaba.compileflow.durable.runtime.action.DurableActionInvoker;
import com.alibaba.compileflow.durable.spi.wait.DurableWaitDescriptionProvider;
import com.alibaba.compileflow.durable.spi.wait.DurableWaitDescriptionContext;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.core.model.action.ActionExecution;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import com.alibaba.compileflow.engine.tbbpm.parser.TbbpmXmlParser;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModel;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class TbbpmDurableEffectActionCompilerTest {
    private TbbpmModel parse(String xml) {
        return TbbpmXmlParser
            .getInstance()
            .parse(FlowSource.of("effect-action", xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void businessTaskActionBecomesAnEffectBoundaryWithoutASecondTargetIdentity() throws Exception {
        CompiledMachineProgram compiled = DurableCompilerTestSupport.compile(parse(flow()), getClass().getClassLoader());
        DurableExecutionContext context = new DurableExecutionContext(compiled.machinePlan(),
                new DurableActionInvoker(ProcessComponentResolver.disabled(), ScriptExecutorRegistry.from(List.of()),
                        getClass().getClassLoader()), DurableWaitDescriptionProvider.defaults(), serializer(compiled));

        FrontierStepResult.EffectWaiting waiting = (FrontierStepResult.EffectWaiting) advanceStart(compiled.program(),
                Map.of("campaignId", "campaign-1"), context);

        assertThat(waiting.effectRequest().elementId()).isEqualTo("createCoupon");
        assertThat(waiting.effectRequest().input()).containsExactly(Map.entry("campaignId", "campaign-1"));
        assertThat(((ActionPlan) compiled.machinePlan().semanticPlan().requireNode("createCoupon").operation())
            .execution())
            .isEqualTo(ActionExecution.EFFECT);
        assertThat(compiled.machinePlan().requireStep("createCoupon")).isInstanceOf(
                DurableMachinePlan.Step.Effect.class);

        FrontierStepResult.Completed completed = (FrontierStepResult.Completed) advanceAfter(compiled.program(),
                waiting.checkpoint(), waiting.state(),
                new BoundaryCompletion.EffectSucceeded(1, "createCoupon", Map.of("couponId", "coupon-7")), context);
        assertThat(completed.output()).containsEntry("couponId", "coupon-7");
    }

    @Test
    void mutableProcessValuesAreDetachedBeforeApplicationCodeRuns() throws Exception {
        CompiledMachineProgram compiled =
                DurableCompilerTestSupport.compile(parse(mutableInputFlow()), getClass().getClassLoader());
        MutableOrder original = new MutableOrder("order-1");

        FrontierStepResult.EffectWaiting waiting = (FrontierStepResult.EffectWaiting) advanceStart(compiled.program(),
                Map.of("order", original),
                new DurableExecutionContext(compiled.machinePlan(),
                        new DurableActionInvoker(ProcessComponentResolver.disabled(),
                                ScriptExecutorRegistry.from(List.of()), getClass().getClassLoader()),
                        DurableWaitDescriptionProvider.defaults(), serializer(compiled)));

        MutableOrder persisted = (MutableOrder) waiting.state().get("order");
        MutableOrder effectInput = (MutableOrder) waiting.effectRequest().input().get("order");
        assertThat(original.getId()).isEqualTo("order-1");
        assertThat(persisted.getId()).isEqualTo("order-1");
        assertThat(effectInput.getId()).isEqualTo("order-1");
        assertThat(effectInput).isNotSameAs(original).isNotSameAs(persisted);
    }

    @Test
    void freezesTheRecoveryPlanSelectedByCurrentProcessState() throws Exception {
        CompiledMachineProgram compiled =
                DurableCompilerTestSupport.compile(parse(dynamicRecoveryFlow()), getClass().getClassLoader());
        DurableExecutionContext context = new DurableExecutionContext(compiled.machinePlan(),
                new DurableActionInvoker(ProcessComponentResolver.disabled(), ScriptExecutorRegistry.from(List.of()),
                        getClass().getClassLoader()), DurableWaitDescriptionProvider.defaults(), serializer(compiled));
        EffectRecoveryPlan selected = EffectRecoveryPlan.retry(7, Duration.ofSeconds(3), Duration.ofMinutes(9));

        FrontierStepResult.EffectWaiting waiting = (FrontierStepResult.EffectWaiting) advanceStart(compiled.program(),
                Map.of("campaignId", "campaign-1", "recoveryPlan", selected), context);

        assertThat(waiting.effectRequest().recoveryPlan()).isEqualTo(selected);
        assertThat(waiting.effectRequest().input()).containsExactly(Map.entry("campaignId", "campaign-1"));
    }

    @Test
    void waitDescriptionReceivesDetachedState() throws Exception {
        CompiledMachineProgram compiled =
                DurableCompilerTestSupport.compile(parse(mutableWaitFlow()), getClass().getClassLoader());
        MutableOrder original = new MutableOrder("order-1");
        DurableWaitDescriptionProvider mutatingWait = new DurableWaitDescriptionProvider() {
            @Override
            public Map<String, Object> describeWait(DurableWaitDescriptionContext context) {
                assertThat(context.processCode()).isEqualTo("mutable.wait.input");
                assertThat(context.semanticDigest()).isEqualTo(compiled.machinePlan().semanticPlan().getDigest());
                assertThat(context.nodeId()).isEqualTo("approval");
                ((MutableOrder) context.state().get("order")).setId("mutated-by-wait");
                return Map.of();
            }
        };

        FrontierStepResult.Waiting waiting = (FrontierStepResult.Waiting) advanceStart(compiled.program(),
                Map.of("order", original),
                new DurableExecutionContext(compiled.machinePlan(), DurableActionInvoker.unavailable(), mutatingWait,
                        serializer(compiled)));

        assertThat(original.getId()).isEqualTo("order-1");
        assertThat(((MutableOrder) waiting.state().get("order")).getId()).isEqualTo("order-1");
    }

    private String flow() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="effect.action" name="Effect Action">
                <var name="campaignId" dataType="java.lang.String" inOutType="param"/>
                <var name="couponId" dataType="java.lang.String" inOutType="return"/>
                <start id="start" g="0,0,32,32"><transition to="createCoupon"/></start>
                <autoTask id="createCoupon" g="60,0,100,40">
                    <action type="java" execution="effect" class="%s" method="create">
                            <input target="campaignId" dataType="java.lang.String"
                                 source="campaignId"/>
                            <output dataType="java.lang.String"
                                 target="couponId"/>

                    </action>
                    <transition to="end"/>
                </autoTask>
                <end id="end" g="200,0,32,32"/>
            </bpm>
            """
            .formatted(CouponActions.class.getName());
    }

    private String mutableInputFlow() {
        return """
            <bpm code="mutable.action.input">
              <var name="order" dataType="%s" inOutType="param"/>
              <start id="start" g="0,0,32,32"><transition to="mutate"/></start>
              <autoTask id="mutate" g="50,0,100,40">
                <action type="java" execution="replayable" class="%s" method="mutate">
                    <input target="order" dataType="%s" source="order"/>

                </action>
                <transition to="dispatch"/>
              </autoTask>
              <autoTask id="dispatch" g="180,0,100,40">
                <action type="java" execution="effect" class="%s" method="dispatch">
                    <input target="order" dataType="%s" source="order"/>

                </action>
                <transition to="end"/>
              </autoTask>
              <end id="end" g="320,0,32,32"/>
            </bpm>
            """
            .formatted(MutableOrder.class.getName(), MutatingActions.class.getName(), MutableOrder.class.getName(),
                    MutatingActions.class.getName(), MutableOrder.class.getName());
    }

    private String dynamicRecoveryFlow() {
        return """
            <bpm code="dynamic.effect.recovery">
              <var name="campaignId" dataType="java.lang.String" inOutType="param"/>
              <var name="recoveryPlan" dataType="%s" inOutType="param"/>
              <start id="start" g="0,0,32,32"><transition to="dispatch"/></start>
              <autoTask id="dispatch" g="80,0,100,40">
                <action type="java" execution="effect" class="%s" method="dispatch">
                    <input target="campaignId" dataType="java.lang.String"
                         source="campaignId"/>
                    <input target="effectId" dataType="java.lang.String"
                         source="__cf_effect_id"/>

                  <effectPolicy recoveryPlanVariable="recoveryPlan"/>
                </action>
                <transition to="end"/>
              </autoTask>
              <end id="end" g="220,0,32,32"/>
            </bpm>
            """
            .formatted(EffectRecoveryPlan.class.getName(), CouponActions.class.getName());
    }

    private String mutableWaitFlow() {
        return """
            <bpm code="mutable.wait.input">
              <var name="order" dataType="%s" inOutType="param"/>
              <start id="start" g="0,0,32,32"><transition to="approval"/></start>
              <waitEventTask id="approval" event="approved" g="80,0,100,40">
                <transition to="end"/>
              </waitEventTask>
              <end id="end" g="220,0,32,32"/>
            </bpm>
            """
            .formatted(MutableOrder.class.getName());
    }

    private DurableValueSerializer serializer(CompiledMachineProgram compiled) {
        return new DurableValueSerializer(compiled.machinePlan());
    }

    public static final class CouponActions {
        public String create(String campaignId) {
            throw new AssertionError("Effect Action must not run in the Turn lane");
        }

        public void dispatch(String campaignId, String effectId) {
            throw new AssertionError("Effect Action must not run in the Turn lane");
        }
    }

    public static final class MutatingActions {
        public void mutate(MutableOrder order) {
            order.setId("mutated-by-action");
        }

        public void dispatch(MutableOrder order) {
            throw new AssertionError("Effect Action must not run in the Turn lane");
        }
    }

    public static final class MutableOrder {
        private String id;

        public MutableOrder() {
        }

        public MutableOrder(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }
}
