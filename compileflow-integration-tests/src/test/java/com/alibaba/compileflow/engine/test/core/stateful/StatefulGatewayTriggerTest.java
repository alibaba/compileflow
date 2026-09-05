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
package com.alibaba.compileflow.engine.test.core.stateful;

import com.alibaba.compileflow.engine.ProcessDefinition;
import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.ProcessTrigger;
import com.alibaba.compileflow.engine.test.support.config.ProcessEngineTestConfiguration;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessContextBuilder;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessEngineTestFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProcessEngineTestConfiguration.class)
@DisplayName("Stateful Gateway Trigger Tests")
@Tag("integration")
@Tag("stateful")
// Depends on concurrency and timing assertions; avoid parallel jitter
@Execution(ExecutionMode.SAME_THREAD)
public class StatefulGatewayTriggerTest {
    protected ProcessEngine engine;

    static Stream<org.junit.jupiter.params.provider.Arguments> exclusiveGatewayCases() {
        return Stream
            // caseName, amount, creditScore, triggerNodeId, triggerEvent, extraKey,
            .of(org.junit.jupiter.params.provider.Arguments
                        // extraValue, expectedFinal
                        .of("high amount approval", 15000, 750, "waitHighAmountApproval", "highAmountApprovalComplete",
                                "approval_reason", "excellent_credit", "combined_approved_excellent_credit"),
                    org.junit.jupiter.params.provider.Arguments.of("low amount approval", 5000, 650,
                            "waitLowAmountApproval", "lowAmountApprovalComplete", null, null,
                            "combined_approved_loan_application"),
                    org.junit.jupiter.params.provider.Arguments.of("rejection", 20000, 500, "waitRejection",
                            "rejectionComplete", "rejection_reason", "poor_credit",
                            "combined_poor_credit_loan_application"));
    }

    @BeforeEach
    void setUp() {
        engine = ProcessEngineTestFactory.createTbbpm();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    @DisplayName("should omit an empty pause guard after a suspending gateway")
    void shouldOmitEmptyPauseGuardAfterSuspendingGateway() {
        String source = engine
            .tooling()
            .generateJavaCode(ProcessDefinition.classpath("bpm.stateful.complexExclusiveGateway",
                    "bpm/stateful/complexExclusiveGateway.bpm"));

        assertThat(source)
            .contains("return _cf$runWaitHighAmountApproval();")
            .contains("return _cf$runWaitLowAmountApproval();")
            .contains("return _cf$runWaitRejection();")
            .doesNotContain("$N", "if (!_cf$paused) {\n" + "        }");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("exclusiveGatewayCases")
    @DisplayName("should handle exclusive gateway with multiple conditional triggers")
    void routesExclusiveGatewayTriggersByCondition(String caseName, int amount, int creditScore, String triggerNodeId,
            String triggerEvent, String extraKey, String extraValue, String expectedFinal) {
        String code = "bpm.stateful.complexExclusiveGateway";
        // Input: process context containing fields required for triggering branches.
        Map<String, Object> context = ProcessContextBuilder
            .newContext()
            .with("requestType", "loan_application")
            .with("amount", amount)
            .with("creditScore", creditScore)
            // default approval result is used by both approval paths
            .with("high_approval_result", "approved")
            .with("low_approval_result", "approved")
            .build();

        Map<String, Object> triggerContext = new HashMap<>(context);
        if (extraKey != null) {
            triggerContext.put(extraKey, extraValue);
        }

        ProcessResult<Map<String, Object>> result = engine.trigger(ProcessDefinition.classpath(code,
                        code.replace(".", "/") + ".bpm"), ProcessTrigger.on(triggerNodeId, triggerEvent), triggerContext);
        // Assert (unified interface): success + return var key exists + value determined
        com.alibaba.compileflow.engine.test.support.helpers.TriggerAssertions.assertSuccessAndHasKey(result,
                "finalResult", caseName + " should succeed");
        assertThat(result.getOutput()).containsEntry("finalResult", expectedFinal);
    }

    @Test
    @DisplayName("should reject waiting nodes inside a parallel region")
    void shouldRejectWaitingNodesInsideParallelRegion() {
        assertUnsupportedConcurrentWait("bpm.stateful.complexParallelGateway",
                Map.of("orderId", "order_12345", "customerId", "customer_67890"));
    }

    @Test
    @DisplayName("should reject waiting nodes inside an inclusive region")
    void shouldRejectWaitingNodesInsideInclusiveRegion() {
        assertUnsupportedConcurrentWait("bpm.stateful.complexInclusiveGateway",
                Map.of("orderType", "premium", "customerTier", "gold", "hasSpecialRequests", true));
    }

    private void assertUnsupportedConcurrentWait(String code, Map<String, Object> context) {
        ProcessResult<Map<String, Object>> result =
                engine.execute(ProcessDefinition.classpath(code, code.replace(".", "/") + ".bpm"), context);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().getCode()).isEqualTo(ErrorCode.CF_VALIDATION_005.getCode());
    }
}
