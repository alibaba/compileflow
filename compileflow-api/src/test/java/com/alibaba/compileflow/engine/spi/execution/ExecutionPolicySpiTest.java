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
package com.alibaba.compileflow.engine.spi.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.ProcessModelType;
import org.junit.jupiter.api.Test;

class ExecutionPolicySpiTest {
    private static final String SOURCE_DIGEST = "a".repeat(64);

    @Test
    void exposesValidatedImmutableFailureDetails() {
        IllegalStateException cause = new IllegalStateException("failed");
        ActionExecutionContext action = new ActionExecutionContext("process-1", "default", "order.process",
                ProcessModelType.TBBPM, SOURCE_DIGEST, "task", 3L, 2);
        FailureContext context = new FailureContext(action, cause);

        assertThat(action.getNamespace()).isEqualTo("default");
        assertThat(action.getProcessCode()).isEqualTo("order.process");
        assertThat(action.getModelType()).isEqualTo(ProcessModelType.TBBPM);
        assertThat(action.getSourceDigest()).isEqualTo(SOURCE_DIGEST);
        assertThat(context.getNodeId()).isEqualTo("task");
        assertThat(context.getInvocationKey()).startsWith("cfai_");
        assertThat(context.getActionExecution()).isSameAs(action);
        assertThat(context.getCause()).isSameAs(cause);
        assertThat(context.getAttemptCount()).isEqualTo(2);
        assertThat(context.toString()).contains("causeType=java.lang.IllegalStateException").doesNotContain("failed");
        assertThatThrownBy(() -> new ActionExecutionContext("process-1", "default", "order.process",
                ProcessModelType.TBBPM, SOURCE_DIGEST, " ", 1L, 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("nodeId");
        assertThatThrownBy(() -> new ActionExecutionContext("process-1", "default", "order.process",
                ProcessModelType.TBBPM, SOURCE_DIGEST, " task ", 1L, 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("nodeId must not contain surrounding whitespace");
        assertThatThrownBy(() -> new ActionExecutionContext("process-1", "default", "order.process",
                ProcessModelType.TBBPM, SOURCE_DIGEST, "task", 1L, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("attemptNumber");
        assertThatThrownBy(() -> new ActionExecutionContext("process-1", "default", "order.process",
                ProcessModelType.TBBPM, "source-1", "task", 1L, 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("SHA-256");
    }

    @Test
    void localActionContextUsesTheCanonicalNamespace() {
        ActionExecutionContext action = new ActionExecutionContext("process-1", "default", "order.process",
                ProcessModelType.TBBPM, SOURCE_DIGEST, "task", 1L, 1);

        assertThat(action.getNamespace()).isEqualTo("default");
        assertThat(action.getInvocationKey()).startsWith("cfai_");
    }

    @Test
    void bindsAndRestoresNestedActionContexts() {
        ActionExecutionContext outer = new ActionExecutionContext("process-1", "default", "order.process",
                ProcessModelType.TBBPM, SOURCE_DIGEST, "outer", 1L, 1);
        ActionExecutionContext inner = new ActionExecutionContext("process-1", "default", "order.process",
                ProcessModelType.TBBPM, SOURCE_DIGEST, "inner", 1L, 1);

        assertThat(ActionExecutionContext.currentOptional()).isEmpty();
        try (ActionExecutionContext.Scope outerScope = ActionExecutionContext.open(outer)) {
            assertThat(outerScope).isNotNull();
            assertThat(ActionExecutionContext.current()).isSameAs(outer);
            try (ActionExecutionContext.Scope suspendedScope = ActionExecutionContext.suspend()) {
                assertThat(suspendedScope).isNotNull();
                assertThat(ActionExecutionContext.currentOptional()).isEmpty();
            }
            try (ActionExecutionContext.Scope innerScope = ActionExecutionContext.open(inner)) {
                assertThat(innerScope).isNotNull();
                assertThat(ActionExecutionContext.current()).isSameAs(inner);
            }
            assertThat(ActionExecutionContext.current()).isSameAs(outer);
        }
        assertThat(ActionExecutionContext.currentOptional()).isEmpty();
    }

    @Test
    void rejectsOutOfOrderScopesEvenWhenTheyBindTheSameContext() {
        ActionExecutionContext action = new ActionExecutionContext("process-1", "default", "order.process",
                ProcessModelType.TBBPM, SOURCE_DIGEST, "task", 1L, 1);
        try (ActionExecutionContext.Scope outer = ActionExecutionContext.open(action)) {
            try (ActionExecutionContext.Scope inner = ActionExecutionContext.open(action)) {
                assertThat(inner).isNotNull();
                assertThatThrownBy(outer::close).isInstanceOf(IllegalStateException.class).hasMessageContaining("LIFO");
                assertThat(ActionExecutionContext.current()).isSameAs(action);
            }
            assertThat(ActionExecutionContext.current()).isSameAs(action);
        }
        assertThat(ActionExecutionContext.currentOptional()).isEmpty();
    }

    @Test
    void rejectsOutOfOrderNestedSuspensions() {
        ActionExecutionContext action = new ActionExecutionContext("process-1", "default", "order.process",
                ProcessModelType.TBBPM, SOURCE_DIGEST, "task", 1L, 1);
        try (ActionExecutionContext.Scope bound = ActionExecutionContext.open(action)) {
            assertThat(bound).isNotNull();
            try (ActionExecutionContext.Scope outer = ActionExecutionContext.suspend()) {
                try (ActionExecutionContext.Scope inner = ActionExecutionContext.suspend()) {
                    assertThat(inner).isNotNull();
                    assertThatThrownBy(outer::close).isInstanceOf(IllegalStateException.class).hasMessageContaining(
                            "LIFO");
                    assertThat(ActionExecutionContext.currentOptional()).isEmpty();
                }
                assertThat(ActionExecutionContext.currentOptional()).isEmpty();
            }
            assertThat(ActionExecutionContext.current()).isSameAs(action);
        }
        assertThat(ActionExecutionContext.currentOptional()).isEmpty();
    }
}
