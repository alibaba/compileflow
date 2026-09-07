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

import com.alibaba.compileflow.examples.order.api.OrderItem;
import com.alibaba.compileflow.examples.order.api.OrderRequest;
import java.net.ConnectException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/**
 * Host-owned business capabilities invoked by the process through Spring bean actions.
 *
 * @author yusu
 */
@Service
public class OrderOperations {
    private static final Set<String> CUSTOMER_TIERS = Set.of("BASIC", "GOLD", "PLATINUM");
    private final ConcurrentMap<String, AtomicInteger> paymentAttempts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Integer> completedPaymentAttempts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PaymentAuthorization> paymentAuthorizations = new ConcurrentHashMap<>();

    public void validate(OrderRequest order) {
        if (order == null || order.orderId() == null || order.orderId().isBlank()) {
            throw new IllegalArgumentException("orderId is required");
        }
        if (!CUSTOMER_TIERS.contains(order.customerTier())) {
            throw new IllegalArgumentException("customerTier must be BASIC, GOLD, or PLATINUM");
        }
        if (order.destinationCountry() == null || !order.destinationCountry().matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("destinationCountry must be a two-letter uppercase country code");
        }
        if (order.items().isEmpty() || order.items().stream().anyMatch(this::invalidItem)) {
            throw new IllegalArgumentException("items must contain valid SKU and quantity values");
        }
        if (order.subtotalCents() <= 0) {
            throw new IllegalArgumentException("subtotalCents must be positive");
        }
        if (order.paymentFailUntilAttempt() < 1 || order.paymentFailUntilAttempt() > 4) {
            throw new IllegalArgumentException("paymentFailUntilAttempt must be between 1 and 4");
        }
    }

    public Integer scoreRisk(OrderRequest order) {
        if (order.fraudSignal()) {
            return 90;
        }
        return order.subtotalCents() >= 100_000 ? 60 : 10;
    }

    public String reserveInventory(OrderRequest order) {
        if (order
            .items()
            .stream()
            .anyMatch(item -> item.sku().startsWith("OOS-"))) {
            throw new IllegalStateException("inventory is unavailable");
        }
        return "RSV-" + order.orderId();
    }

    public String authorizePayment(String orderId, Integer payableCents, Integer failUntilAttempt)
            throws ConnectException {
        PaymentAuthorization existing = paymentAuthorizations.get(orderId);
        if (existing != null) {
            return existing.requireSameAmount(payableCents);
        }
        AtomicInteger counter = paymentAttempts.computeIfAbsent(orderId, ignored -> new AtomicInteger());
        int attempt = counter.incrementAndGet();
        if (attempt < failUntilAttempt) {
            throw new ConnectException("payment provider is temporarily unavailable");
        }
        PaymentAuthorization candidate = new PaymentAuthorization(payableCents, "PAY-" + orderId + "-" + payableCents);
        PaymentAuthorization committed = paymentAuthorizations.putIfAbsent(orderId, candidate);
        PaymentAuthorization result = committed == null ? candidate : committed;
        String reference = result.requireSameAmount(payableCents);
        completedPaymentAttempts.putIfAbsent(orderId, attempt);
        paymentAttempts.remove(orderId, counter);
        return reference;
    }

    public String appendShipmentLine(String current, OrderItem item) {
        String line = item.sku() + "x" + item.quantity();
        return current == null || current.isEmpty() ? line : current + "," + line;
    }

    public String notifyCustomer(String orderId) {
        return "ORDER_CONFIRMED:" + orderId;
    }

    public String publishLoyaltyEvent(String orderId, String customerTier) {
        return "LOYALTY:" + customerTier + ":" + orderId;
    }

    public String createCustomsDocument(String orderId, String destinationCountry) {
        return "CUSTOMS:" + destinationCountry + ":" + orderId;
    }

    public String requestGiftPacking(String orderId) {
        return "GIFT_PACK:" + orderId;
    }

    public String decisionAudit(String orderId, String status, Integer riskScore) {
        return "order=" + orderId + ";status=" + status + ";risk=" + riskScore;
    }

    public String fulfillmentAudit(String orderId, String status, Integer payableCents, String inventoryReservation,
            String paymentAuthorization, String shipmentPlan) {
        return "order=" + orderId + ";status=" + status + ";payable=" + payableCents + ";inventory="
                + inventoryReservation + ";payment=" + paymentAuthorization + ";shipment=" + shipmentPlan;
    }

    public int paymentAttemptsFor(String orderId) {
        Integer completed = completedPaymentAttempts.get(orderId);
        if (completed != null) {
            return completed;
        }
        AtomicInteger active = paymentAttempts.get(orderId);
        return active == null ? 0 : active.get();
    }

    private boolean invalidItem(OrderItem item) {
        return item == null || item.sku() == null || item.sku().isBlank() || item.quantity() <= 0;
    }

    /**
     * Simulates the payment provider's idempotency ledger. Reusing one business key with a
     * different amount is a conflict, never a second authorization.
     */
    private record PaymentAuthorization(int payableCents, String reference) {
        private String requireSameAmount(int requestedPayableCents) {
            if (payableCents != requestedPayableCents) {
                throw new IllegalStateException("payment idempotency key was reused with a different amount");
            }
            return reference;
        }
    }
}
