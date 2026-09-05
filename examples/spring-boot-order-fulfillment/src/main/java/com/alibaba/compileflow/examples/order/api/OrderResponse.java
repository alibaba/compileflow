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

/**
 * Controlled business output produced by the fulfillment process.
 *
 * @param orderId               merchant order identifier
 * @param status                FULFILLED, REVIEW_REQUIRED, or REJECTED
 * @param riskScore             deterministic risk score
 * @param discountCents         applied tier discount
 * @param payableCents          amount authorized by payment
 * @param inventoryReservation inventory reservation reference
 * @param paymentAuthorization payment authorization reference
 * @param shipmentPlan          comma-separated physical shipment lines
 * @param customerNotification customer notification event
 * @param loyaltyEvent          optional loyalty event
 * @param customsDocument       optional customs document
 * @param giftPacking           optional gift-packing instruction
 * @param auditTrail            bounded execution summary
 * @author yusu
 */
public record OrderResponse(String orderId, String status, int riskScore, int discountCents, int payableCents,
        String inventoryReservation, String paymentAuthorization, String shipmentPlan, String customerNotification,
        String loyaltyEvent, String customsDocument, String giftPacking, String auditTrail) {}
