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
package com.alibaba.compileflow.engine.core.runtime.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.core.model.action.EffectiveInvocationPolicy;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionInvocation;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ReconcilePlan;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProcessActionInvokerTest {
    private final ProcessActionInvoker invoker = new ProcessActionInvoker(ProcessComponentResolver.disabled(),
            ScriptExecutorRegistry.from(List.of()), getClass().getClassLoader());

    @Test
    void reconcileReadsPersistedSourceFieldInsteadOfInvocationTarget() throws Exception {
        ReconcilePlan plan = new ReconcilePlan(new ActionInvocation.Java(ReconcileTarget.class.getName(), "lookup"),
                List.of(ReconcilePlan.Input.requestField("request_id", "id", String.class.getName())));

        assertThat(invoker.invokeRaw(plan, Map.of("request_id", "order-42"), "effect-1")).isEqualTo("found:order-42");
    }

    @Test
    void reconcileRejectsMissingPersistedSourceField() {
        ReconcilePlan plan = new ReconcilePlan(new ActionInvocation.Java(ReconcileTarget.class.getName(), "lookup"),
                List.of(ReconcilePlan.Input.requestField("request_id", "id", String.class.getName())));

        assertThatThrownBy(() -> invoker.invokeRaw(plan, Map.of("id", "wrong-field"), "effect-1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Persisted Effect input is missing: request_id");
    }

    @Test
    void structuralReadinessValidatesDeclaredActionResultType() {
        ActionInvocation invocation = new ActionInvocation.Java(ActionTarget.class.getName(), "text");

        assertThat(invoker.isStructurallyReady(action(invocation, String.class.getName()))).isTrue();
        assertThat(invoker.isStructurallyReady(action(invocation, Object.class.getName()))).isTrue();
        assertThat(invoker.isStructurallyReady(action(invocation, Integer.class.getName()))).isFalse();
    }

    @Test
    void structuralReadinessIgnoresReturnTypeWithoutOutputMapping() {
        ActionPlan action = new ActionPlan(null, new ActionInvocation.Java(ActionTarget.class.getName(), "text"),
                List.of(), null, EffectiveInvocationPolicy.defaults(), null);

        assertThat(invoker.isStructurallyReady(action)).isTrue();
    }

    private static ActionPlan action(ActionInvocation invocation, String resultType) {
        return new ActionPlan(null, invocation, List.of(), new ActionPlan.Output("result", resultType, resultType),
                EffectiveInvocationPolicy.defaults(), null);
    }

    public static final class ReconcileTarget {
        public String lookup(String id) {
            return "found:" + id;
        }
    }

    public static final class ActionTarget {
        public String text() {
            return "value";
        }
    }
}
