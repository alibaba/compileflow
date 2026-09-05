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
package com.alibaba.compileflow.examples.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.alibaba.compileflow.examples.order.api.OrderItem;
import com.alibaba.compileflow.examples.order.api.OrderRequest;
import com.alibaba.compileflow.examples.order.api.OrderResponse;
import com.alibaba.compileflow.examples.order.workflow.OrderFulfillmentWorkflow;
import com.alibaba.compileflow.examples.order.workflow.OrderOperations;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class OrderFulfillmentApplicationTest {
    private final OrderFulfillmentWorkflow workflow;
    private final OrderOperations operations;
    private final WebApplicationContext applicationContext;
    private MockMvc mockMvc;

    @Autowired
    OrderFulfillmentApplicationTest(OrderFulfillmentWorkflow workflow, OrderOperations operations,
            WebApplicationContext applicationContext) {
        this.workflow = workflow;
        this.operations = operations;
        this.applicationContext = applicationContext;
    }

    @BeforeEach
    void createHttpClient() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
    }

    @Test
    void fulfillsInternationalGiftOrderAndRecoversTransientPaymentFailure() {
        OrderRequest request = request("order-1001", "PLATINUM", "US", 12_000, 2, false, true);

        OrderResponse response = workflow.fulfill(request, "fulfillment-test-1001");

        assertThat(response.status()).isEqualTo("FULFILLED");
        assertThat(response.riskScore()).isEqualTo(10);
        assertThat(response.discountCents()).isEqualTo(2_400);
        assertThat(response.payableCents()).isEqualTo(9_600);
        assertThat(response.inventoryReservation()).isEqualTo("RSV-order-1001");
        assertThat(response.paymentAuthorization()).isEqualTo("PAY-order-1001-9600");
        assertThat(response.shipmentPlan()).isEqualTo("SKU-PHYSICALx2");
        assertThat(response.customerNotification()).isEqualTo("ORDER_CONFIRMED:order-1001");
        assertThat(response.loyaltyEvent()).isEqualTo("LOYALTY:PLATINUM:order-1001");
        assertThat(response.customsDocument()).isEqualTo("CUSTOMS:US:order-1001");
        assertThat(response.giftPacking()).isEqualTo("GIFT_PACK:order-1001");
        assertThat(response.auditTrail()).contains("status=FULFILLED", "shipment=SKU-PHYSICALx2");
        assertThat(operations.paymentAttemptsFor("order-1001")).isEqualTo(2);
    }

    @Test
    void routesHighValueOrderToManualReviewWithoutExternalFulfillment() {
        OrderRequest request = request("order-2001", "BASIC", "CN", 100_000, 1, false, false);

        OrderResponse response = workflow.fulfill(request, "fulfillment-test-2001");

        assertThat(response.status()).isEqualTo("REVIEW_REQUIRED");
        assertThat(response.riskScore()).isEqualTo(60);
        assertThat(response.inventoryReservation()).isEqualTo("NOT_APPLICABLE");
        assertThat(response.paymentAuthorization()).isEqualTo("NOT_APPLICABLE");
        assertThat(response.auditTrail()).isEqualTo("order=order-2001;status=REVIEW_REQUIRED;risk=60");
        assertThat(operations.paymentAttemptsFor("order-2001")).isZero();
    }

    @Test
    void rejectsOrderWithFraudSignal() {
        OrderRequest request = request("order-3001", "GOLD", "CN", 20_000, 1, true, false);

        OrderResponse response = workflow.fulfill(request, "fulfillment-test-3001");

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.riskScore()).isEqualTo(90);
        assertThat(response.auditTrail()).isEqualTo("order=order-3001;status=REJECTED;risk=90");
    }

    @Test
    void exposesTheWorkflowThroughHttp() throws Exception {
        String request =
                """
                {
                  "orderId": "order-http-1",
                  "customerTier": "BASIC",
                  "destinationCountry": "CN",
                  "items": [
                    {"sku": "SKU-PHYSICAL", "quantity": 2, "digital": false},
                    {"sku": "DIGITAL-LICENSE", "quantity": 1, "digital": true}
                  ],
                  "subtotalCents": 12000,
                  "paymentFailUntilAttempt": 1,
                  "fraudSignal": false,
                  "giftOrder": false
                }
                """;

        mockMvc
            .perform(post("/api/orders/fulfill")
                .header("X-Invocation-Id", "http-test-1")
                .contentType("application/json")
                .content(request))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FULFILLED"))
            .andExpect(jsonPath("$.payableCents").value(12_000))
            .andExpect(jsonPath("$.shipmentPlan").value("SKU-PHYSICALx2"))
            .andExpect(jsonPath("$.loyaltyEvent").value("NOT_APPLICABLE"))
            .andExpect(jsonPath("$.customsDocument").value("NOT_APPLICABLE"));
    }

    @Test
    void returnsControlledHttpErrorAfterPaymentRetriesAreExhausted() throws Exception {
        String request =
                """
                {
                  "orderId": "order-payment-down",
                  "customerTier": "BASIC",
                  "destinationCountry": "CN",
                  "items": [{"sku": "SKU-PHYSICAL", "quantity": 1, "digital": false}],
                  "subtotalCents": 12000,
                  "paymentFailUntilAttempt": 4,
                  "fraudSignal": false,
                  "giftOrder": false
                }
                """;

        mockMvc
            .perform(post("/api/orders/fulfill")
                .header("X-Invocation-Id", "http-payment-down")
                .contentType("application/json")
                .content(request))
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.errorCode").isNotEmpty())
            .andExpect(jsonPath("$.message").isNotEmpty());
        assertThat(operations.paymentAttemptsFor("order-payment-down")).isEqualTo(3);
    }

    private static OrderRequest request(String orderId, String customerTier, String country, int subtotalCents,
            int paymentFailUntilAttempt, boolean fraudSignal, boolean giftOrder) {
        return new OrderRequest(orderId, customerTier, country,
                List.of(new OrderItem("SKU-PHYSICAL", 2, false), new OrderItem("DIGITAL-LICENSE", 1, true)),
                subtotalCents, paymentFailUntilAttempt, fraudSignal, giftOrder);
    }
}
