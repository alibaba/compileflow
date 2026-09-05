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
package com.alibaba.compileflow.deploy.control.repository;

/**
 * Immutable persistence model for one routing-outbox delivery record.
 *
 * @author yusu
 */
public final class RoutingOutboxRecord {
    public static final String ALIAS_STATE_EVENT_TYPE = "ALIAS_STATE";
    private final long id;
    private final String eventType;
    private final String namespace;
    private final String code;
    private final String alias;
    private final String routingKey;
    private final String payload;
    private final Status status;
    private final int attemptCount;
    private final Long nextAttemptAt;
    private final String lastError;
    private final String leaseToken;
    private final Long leaseUntil;
    private final long createdAt;
    private final long updatedAt;

    public RoutingOutboxRecord(long id, String eventType, String namespace, String code, String alias, String routingKey,
            String payload, Status status, int attemptCount, Long nextAttemptAt, String lastError, String leaseToken,
            Long leaseUntil, long createdAt, long updatedAt) {
        this.id = id;
        this.eventType = eventType;
        this.namespace = namespace;
        this.code = code;
        this.alias = alias;
        this.routingKey = routingKey;
        this.payload = payload;
        this.status = status;
        this.attemptCount = attemptCount;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = lastError;
        this.leaseToken = leaseToken;
        this.leaseUntil = leaseUntil;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getCode() {
        return code;
    }

    public String getAlias() {
        return alias;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public String getPayload() {
        return payload;
    }

    public Status getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Long getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public String getLeaseToken() {
        return leaseToken;
    }

    public Long getLeaseUntil() {
        return leaseUntil;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public enum Status {
        PENDING,
        PROCESSING,
        DELIVERED,
        FAILED
    }
}
