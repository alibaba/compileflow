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
package com.alibaba.compileflow.examples.order.workflow;

import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessExecutionOptions;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightOptions;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightReport;
import com.alibaba.compileflow.examples.order.api.OrderRequest;
import com.alibaba.compileflow.examples.order.api.OrderResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Application service that owns process admission, input mapping, and controlled output mapping.
 *
 * @author yusu
 */
@Service
public class OrderFulfillmentWorkflow {
    static final ProcessDefinition PROCESS = ProcessDefinition.classpath(ProcessModelType.TBBPM,
            "example.order.fulfillment", "flows/order-fulfillment.bpm");
    private final ProcessEngine processEngine;

    public OrderFulfillmentWorkflow(ProcessEngine processEngine, OrderOperations orderOperations) {
        this.processEngine = processEngine;
        ProcessPreflightReport report = processEngine.tooling().preflight(PROCESS, ProcessPreflightOptions.strict());
        if (report.getOverallStatus() != ProcessPreflightReport.OverallStatus.PASS) {
            String diagnostics = report
                .getItems()
                .stream()
                .map(item -> item.getType() + "=" + item.getStatus() + ": " + item.getMessage())
                .collect(Collectors.joining("; "));
            throw new IllegalStateException("order fulfillment preflight failed: " + diagnostics);
        }
    }

    public OrderResponse fulfill(OrderRequest request, String invocationId) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("order", request);
        input.put("items", request.items());
        input.put("orderId", request.orderId());
        input.put("customerTier", request.customerTier());
        input.put("destinationCountry", request.destinationCountry());
        input.put("subtotalCents", request.subtotalCents());
        input.put("paymentFailUntilAttempt", request.paymentFailUntilAttempt());
        input.put("fraudSignal", request.fraudSignal());
        input.put("giftOrder", request.giftOrder());

        String effectiveInvocationId =
                invocationId == null || invocationId.isBlank() ? "order-fulfillment-" + UUID.randomUUID() : invocationId;
        ProcessExecutionOptions options = ProcessExecutionOptions
            .builder()
            .invocationId(effectiveInvocationId)
            .build();
        Map<String, Object> output = processEngine.execute(PROCESS, input, options).orElseThrow();
        return new OrderResponse(request.orderId(), string(output, "status"), integer(output, "riskScore"),
                integer(output, "discountCents"), integer(output, "payableCents"),
                string(output, "inventoryReservation"), string(output, "paymentAuthorization"),
                string(output, "shipmentPlan"), string(output, "customerNotification"), string(output, "loyaltyEvent"),
                string(output, "customsDocument"), string(output, "giftPacking"), string(output, "auditTrail"));
    }

    private static int integer(Map<String, Object> output, String key) {
        return ((Number) output.get(key)).intValue();
    }

    private static String string(Map<String, Object> output, String key) {
        return (String) output.get(key);
    }
}
