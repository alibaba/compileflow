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
package com.alibaba.compileflow.examples.order.api;

import java.util.List;

/**
 * Business input accepted by the fulfillment HTTP endpoint.
 *
 * @param orderId                merchant order identifier
 * @param customerTier            BASIC, GOLD, or PLATINUM
 * @param destinationCountry      two-letter destination country
 * @param items                   physical and digital order lines
 * @param subtotalCents           subtotal before the tier discount
 * @param paymentFailUntilAttempt simulated transient payment failure boundary
 * @param fraudSignal             deterministic fraud signal for the example
 * @param giftOrder               whether gift packing is requested
 * @author yusu
 */
public record OrderRequest(String orderId, String customerTier, String destinationCountry, List<OrderItem> items,
        int subtotalCents, int paymentFailUntilAttempt, boolean fraudSignal, boolean giftOrder) {
    public OrderRequest {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
