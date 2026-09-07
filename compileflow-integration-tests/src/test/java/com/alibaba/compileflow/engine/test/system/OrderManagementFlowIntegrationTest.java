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
package com.alibaba.compileflow.engine.test.system;

import com.alibaba.compileflow.engine.ProcessModelType;
import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.ProcessTrigger;
import com.alibaba.compileflow.engine.test.support.config.ProcessEngineTestConfiguration;
import com.alibaba.compileflow.engine.test.support.fixtures.om.context.BusinessContext;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessEngineTestFactory;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProcessEngineTestConfiguration.class)
@Tag("integration")
@Tag("tbbpm")
@DisplayName("Order management flow integration")
class OrderManagementFlowIntegrationTest {
    private static final String PREFIX = "bpm.order-management.";
    @Autowired
    private ApplicationContext applicationContext;
    private ProcessEngine engine;

    @BeforeEach
    void setUp() {
        engine = ProcessEngineFactory.create(ProcessEngineTestFactory
            .builder()
            .componentResolver(applicationContext::getBean)
            .build());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (engine != null) {
            engine.close();
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"generalOrderFulfillmentFlow", "paySuccessCallbackSubFlow", "paymentHandlingSubFlow",
            "validateBeforeSavingSubFlow", "waitPaySuccessFlow"})
    @DisplayName("all order-management definitions compile")
    void allDefinitionsCompile(String name) {
        ProcessDefinition definition = definition(name);

        engine.runtime().warmUp(definition);

        assertThat(engine.tooling().generateJavaCode(definition))
            .containsPattern("implements (ExecutableProcess|TriggerableProcess)")
            .doesNotContain("com.alibaba.compileflow.engine.test.om");
    }

    @Test
    @DisplayName("validation subflow resolves Spring activities and preserves mutations")
    void validationSubflowExecutesThroughSpringComponents() {
        BusinessContext business = new BusinessContext("order-42");
        business.setOrderAmount(128.50);
        business.setCustomerType("VIP");

        ProcessResult<Map<String, Object>> result =
                engine.execute(definition("validateBeforeSavingSubFlow"), Map.of("BUSINESS_CONTEXT", business));

        assertThat(result.isSuccess()).as("execution failed: %s", result.getError()).isTrue();
        assertThat(business.getAdditionalData())
            .containsEntry("validated", true)
            .containsEntry("promotionValidated", true)
            .containsEntry("promotionEligible", true)
            .containsEntry("priceValidated", true)
            .containsEntry("deliveryValidated", true);
    }

    @Test
    @DisplayName("payment callback subflow executes its complete activity chain")
    void paymentCallbackSubflowExecutesCompleteChain() {
        BusinessContext business = new BusinessContext("order-43");
        business.setOrderAmount(256.00);
        business.setCustomerType("MEMBER");

        ProcessResult<Map<String, Object>> result =
                engine.execute(definition("paySuccessCallbackSubFlow"), Map.of("BUSINESS_CONTEXT", business));

        assertThat(result.isSuccess()).as("execution failed: %s", result.getError()).isTrue();
        assertThat(business.getAdditionalData())
            .containsEntry("priceCalculated", true)
            .containsEntry("cancelReason", "timeout")
            .containsEntry("fundApportioned", true)
            .containsEntry("userTagUpdated", true)
            .containsEntry("paymentStatus", "success")
            .containsEntry("callbackInfoUpdated", true);
    }

    @Test
    @DisplayName("trigger entries execute with caller-supplied state seeds")
    void triggerEntriesUseCallerSuppliedStateSeeds() {
        BusinessContext business = new BusinessContext("order-44");
        business.setOrderAmount(512.00);
        business.setCustomerType("MEMBER");
        business.setPaymentType(new String("online"));
        Map<String, Object> context = new HashMap<>();
        context.put("BUSINESS_CONTEXT", business);

        String code = PREFIX + "generalOrderFulfillmentFlow";
        ProcessDefinition definition = definition("generalOrderFulfillmentFlow");
        String source = engine.tooling().generateJavaCode(definition);
        assertThat(source)
            .startsWith("package com.alibaba.compileflow.generated.process.bpm.order_management;")
            .contains("\"online\".equals(paymentType)")
            .contains("_cf$loadParameters(context);", "_cf$restoreState(context);")
            .contains("case \"32\" -> _cf$resumeManualSecurityCallback();")
            .contains("case \"38\" -> _cf$resumePaymentSuccessCallback();")
            .contains("private int _cf$routeByPaymentType()")
            .contains("private int _cf$routeByAmendmentJudgementFlag()")
            .contains("return _cf$routeToDeliveryOrderCreateOrOrderReverse();")
            .contains("return _cf$runDeliveryOrderCreate();", "return _cf$runOrderReverse();")
            .contains("private void _cf$callValidateBeforeSavingSubFlow()")
            .doesNotContain("_cf$awaitManualSecurityCallback() throws Exception")
            .contains("EngineExecutionContextHolder.requireCurrent()")
            .contains(".callProcess(", "\"37\",",
                    "Collections.singletonMap(\"BUSINESS_CONTEXT\", this.BUSINESS_CONTEXT))")
            .doesNotContain("_cf$executionContext", "allVariables", "context.containsKey(", "_cf$mapParameters",
                    "_cf$mapState", "_cf$triggerNodeId", "_cf$resume32", "_cf$route14", "_cf$route1564467580907")
            .doesNotContain("childProcessRef", "executeChild", "inheritedChildProcessRef", "_cf$childInputs",
                    "_cf$childOutput")
            .doesNotContain("_cf$runDeliveryOrderCreateTo2", "_cf$runOrderReverseTo2")
            .doesNotContain("_cf$signal = _cf$routeToDeliveryOrderCreateOrOrderReverse();")
            .doesNotContain("Map<String, Object> output =")
            .doesNotContain("package compileflow.")
            .doesNotEndWith("\n\n}\n")
            .doesNotContainPattern("private int _cf\\$run[^\\n]*\\{\\n\\s*return _CF_NORMAL;\\n\\s*\\}")
            .doesNotContainPattern("if \\([^\\n]+\\) (?:return|break|continue)[^\\n]*;");
        assertThat(source.lines().mapToInt(String::length).max().orElseThrow()).isLessThanOrEqualTo(120);

        engine.runtime().warmUp(definition);
        ProcessResult<Map<String, Object>> started = engine.execute(ProcessDefinition.classpath(ProcessModelType.TBBPM,
                        code, code.replace(".", "/") + ".bpm"), context);
        assertSuccessful(started, "start");
        context.putAll(started.getOutput());

        ProcessResult<Map<String, Object>> paymentPending = engine.trigger(ProcessDefinition.classpath(ProcessModelType.TBBPM,
                        code, code.replace(".", "/") + ".bpm"), ProcessTrigger.at("29"), context);
        assertSuccessful(paymentPending, "payment pending");
        context.putAll(paymentPending.getOutput());

        ProcessResult<Map<String, Object>> paymentSucceeded = engine.trigger(ProcessDefinition.classpath(ProcessModelType.TBBPM,
                        code, code.replace(".", "/") + ".bpm"), ProcessTrigger.at("38"), context);
        assertSuccessful(paymentSucceeded, "payment success");
        context.putAll(paymentSucceeded.getOutput());

        ProcessResult<Map<String, Object>> delivered = engine.trigger(ProcessDefinition.classpath(ProcessModelType.TBBPM,
                        code, code.replace(".", "/") + ".bpm"), ProcessTrigger.at("33"), context);
        assertSuccessful(delivered, "delivery callback");

        assertThat(business.getAdditionalData())
            .containsEntry("validated", true)
            .containsEntry("groupCreated", true)
            .containsEntry("paymentStatus", "success")
            .containsEntry("inventoryReduced", true)
            .containsEntry("securityValidationStatus", "updated")
            .containsEntry("deliveryStatus", "delivered");
    }

    private static void assertSuccessful(ProcessResult<?> result, String step) {
        assertThat(result.isSuccess()).as("%s failed: %s", step, result.getError()).isTrue();
    }

    private static ProcessDefinition definition(String name) {
        return ProcessDefinition.classpath(ProcessModelType.TBBPM, PREFIX + name,
                "bpm/order-management/" + name + ".bpm");
    }
}
