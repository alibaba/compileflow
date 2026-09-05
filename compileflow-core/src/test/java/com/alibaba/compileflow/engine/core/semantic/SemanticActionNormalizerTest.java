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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.core.model.action.Action;
import com.alibaba.compileflow.engine.core.model.action.EffectPolicy;
import com.alibaba.compileflow.engine.core.model.action.ActionExecution;
import com.alibaba.compileflow.engine.core.model.action.ActionType;
import com.alibaba.compileflow.engine.core.model.action.EffectRecovery;
import com.alibaba.compileflow.engine.core.model.action.ReconcileAction;
import com.alibaba.compileflow.engine.core.model.action.ReconcileInput;
import com.alibaba.compileflow.engine.core.model.mapping.InputMapping;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SemanticActionNormalizerTest {
    @Test
    void lowersEffectIdBindingToTypedSemanticSource() {
        Action action = effectAction();
        InputMapping effectId = new InputMapping();
        effectId.setSource("__cf_effect_id");
        effectId.setTarget("effectId");
        effectId.setDataType(String.class.getName());
        action.addInputMapping(effectId);

        ActionPlan plan = SemanticActionNormalizer.normalize(action, Map.of());

        assertThat(plan.inputs())
            .singleElement()
            .extracting(ActionPlan.Input::source)
            .isInstanceOf(ActionPlan.InputSource.EffectId.class);
    }

    @Test
    void rejectsEffectAttemptAsBusinessInput() {
        Action action = effectAction();
        InputMapping attempt = new InputMapping();
        attempt.setSource("__cf_effect_attempt");
        attempt.setTarget("attempt");
        attempt.setDataType(Integer.class.getName());
        action.addInputMapping(attempt);

        assertThatThrownBy(() -> SemanticActionNormalizer.normalize(action, Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unsupported Effect metadata input source '__cf_effect_attempt'");
    }

    @Test
    void rejectsEffectPolicyWithoutEffectExecution() {
        Action action = new Action();
        action.setEffectPolicy(new EffectPolicy());

        assertThatThrownBy(() -> SemanticActionNormalizer.normalize(action, Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("effectPolicy requires execution=\"effect\"");
    }

    @Test
    void rejectsReconcileInputOutsideThePersistedEffectRequest() {
        Action action = new Action();
        action.setType(ActionType.JAVA);
        action.setClassName("example.Effect");
        action.setExecution(ActionExecution.EFFECT);
        ReconcileAction reconcile = new ReconcileAction();
        reconcile.setType(ActionType.JAVA);
        reconcile.setClassName("example.Reconcile");
        ReconcileInput input = new ReconcileInput();
        input.setSource("missing_request_field");
        input.setTarget("request");
        input.setDataType(String.class.getName());
        reconcile.addInput(input);
        EffectPolicy policy = new EffectPolicy();
        policy.setRecovery(EffectRecovery.RECONCILE);
        policy.setMaxAttempts(1);
        policy.setMaxReconcileAttempts(1);
        policy.setRecoveryDelay("PT1S");
        policy.setReconcileAction(reconcile);
        action.setEffectPolicy(policy);

        assertThatThrownBy(() -> SemanticActionNormalizer.normalize(action, Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Reconcile input references unknown Effect request field 'missing_request_field'");
    }

    @Test
    void excludesInjectedEffectMetadataTargetsFromThePersistedRequest() {
        Action action = new Action();
        action.setType(ActionType.JAVA);
        action.setClassName("example.Effect");
        action.setExecution(ActionExecution.EFFECT);
        InputMapping effectId = new InputMapping();
        effectId.setSource("__cf_effect_id");
        effectId.setTarget("effectId");
        effectId.setDataType(String.class.getName());
        action.addInputMapping(effectId);

        ReconcileAction reconcile = new ReconcileAction();
        reconcile.setType(ActionType.JAVA);
        reconcile.setClassName("example.Reconcile");
        ReconcileInput input = new ReconcileInput();
        input.setSource("effectId");
        input.setTarget("effectId");
        input.setDataType(String.class.getName());
        reconcile.addInput(input);
        EffectPolicy policy = new EffectPolicy();
        policy.setRecovery(EffectRecovery.RECONCILE);
        policy.setMaxAttempts(1);
        policy.setMaxReconcileAttempts(1);
        policy.setRecoveryDelay("PT1S");
        policy.setReconcileAction(reconcile);
        action.setEffectPolicy(policy);

        assertThatThrownBy(() -> SemanticActionNormalizer.normalize(action, Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Reconcile input references unknown Effect request field 'effectId'");
    }

    private static Action effectAction() {
        Action action = new Action();
        action.setType(ActionType.JAVA);
        action.setClassName("example.Effect");
        action.setExecution(ActionExecution.EFFECT);
        EffectPolicy policy = new EffectPolicy();
        policy.setRecovery(EffectRecovery.MANUAL);
        action.setEffectPolicy(policy);
        return action;
    }
}
