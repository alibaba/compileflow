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
package com.alibaba.compileflow.deploy.spi.store;

import java.util.List;

/**
 * Semantic persistence boundary for routing-outbox records awaiting delivery.
 *
 * <p>Rollout mutations append their outbox record inside the provider-owned rollout transaction;
 * the append operations here are reserved for explicit propagation and reconciliation workflows.
 *
 * @author yusu
 */
public interface RoutingOutboxStore {
    List<RoutingOutboxRecord> claimPending(int limit, String claimantId, long leaseDurationMs);

    long countByStatus(RoutingOutboxRecord.Status status);

    long countExpiredClaims();

    int requeueFailed();

    int deleteDeliveredOlderThan(long retentionMs, int limit);

    int markDelivered(long id, String leaseToken);

    long appendPending(String eventType, String namespace, String code, String alias, String routingKey, String payload);

    boolean ensurePending(String eventType, String namespace, String code, String alias, String routingKey,
            String payload);

    int markFailed(long id, String leaseToken, String error, int maxAttempts, long retryDelayMs);
}
