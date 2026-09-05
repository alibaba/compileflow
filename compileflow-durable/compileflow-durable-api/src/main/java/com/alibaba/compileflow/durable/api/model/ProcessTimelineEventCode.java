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
package com.alibaba.compileflow.durable.api.model;

import com.alibaba.compileflow.durable.api.validation.DurableIdentifiers;

/**
 * Open, validated code for one committed Durable Run Timeline fact.
 *
 * <p>This is a value object rather than an enum because Journal diagnostics
 * are not a closed state-machine input. The named constants mirror facts the
 * current first-party Kernel actually emits; openness is not a compatibility
 * promise or an extension protocol.</p>
 *
 * @param value immutable uppercase event code
 * @author yusu
 */
public record ProcessTimelineEventCode(String value) {
    public static final ProcessTimelineEventCode RUN_CREATED = of("RUN_CREATED");
    public static final ProcessTimelineEventCode RUN_LEASE_EXPIRED = of("RUN_LEASE_EXPIRED");
    public static final ProcessTimelineEventCode RUN_CANCEL_REQUESTED = of("RUN_CANCEL_REQUESTED");
    public static final ProcessTimelineEventCode RUN_CANCELLED = of("RUN_CANCELLED");
    public static final ProcessTimelineEventCode RUN_SUCCEEDED = of("RUN_SUCCEEDED");
    public static final ProcessTimelineEventCode RUN_FAILED = of("RUN_FAILED");
    public static final ProcessTimelineEventCode RUN_PAUSE_REQUESTED = of("RUN_PAUSE_REQUESTED");
    public static final ProcessTimelineEventCode RUN_PAUSED = of("RUN_PAUSED");
    public static final ProcessTimelineEventCode RUN_RESUMED = of("RUN_RESUMED");
    public static final ProcessTimelineEventCode TURN_FAULT_BACKOFF_MILESTONE = of("TURN_FAULT_BACKOFF_MILESTONE");
    public static final ProcessTimelineEventCode WAIT_COMMITTED = of("WAIT_COMMITTED");
    public static final ProcessTimelineEventCode WAIT_COMPLETED = of("WAIT_COMPLETED");
    public static final ProcessTimelineEventCode WAIT_EXPIRED = of("WAIT_EXPIRED");
    public static final ProcessTimelineEventCode TIMER_COMMITTED = of("TIMER_COMMITTED");
    public static final ProcessTimelineEventCode TIMER_FIRED = of("TIMER_FIRED");
    public static final ProcessTimelineEventCode EFFECT_COMMITTED = of("EFFECT_COMMITTED");
    public static final ProcessTimelineEventCode EFFECT_COMPLETED = of("EFFECT_COMPLETED");
    public static final ProcessTimelineEventCode EFFECT_OUTCOME_UNKNOWN = of("EFFECT_OUTCOME_UNKNOWN");
    public static final ProcessTimelineEventCode EFFECT_READINESS_BACKOFF = of("EFFECT_READINESS_BACKOFF");
    public static final ProcessTimelineEventCode EFFECT_REDISPATCH_SCHEDULED = of("EFFECT_REDISPATCH_SCHEDULED");
    public static final ProcessTimelineEventCode EFFECT_REVIEW_REQUIRED = of("EFFECT_REVIEW_REQUIRED");
    public static final ProcessTimelineEventCode EFFECT_LEASE_EXPIRED_UNKNOWN = of("EFFECT_LEASE_EXPIRED_UNKNOWN");
    public static final ProcessTimelineEventCode EFFECT_RESOLVED_CONFIRM_SUCCEEDED =
            of("EFFECT_RESOLVED_CONFIRM_SUCCEEDED");
    public static final ProcessTimelineEventCode EFFECT_RESOLVED_CONFIRM_NOT_EXECUTED_RETRY =
            of("EFFECT_RESOLVED_CONFIRM_NOT_EXECUTED_RETRY");
    public static final ProcessTimelineEventCode EFFECT_RESOLVED_FAIL_RUN = of("EFFECT_RESOLVED_FAIL_RUN");
    public static final ProcessTimelineEventCode OUTBOX_RESOLVED_PENDING = of("OUTBOX_RESOLVED_PENDING");
    public static final ProcessTimelineEventCode OUTBOX_RESOLVED_ABANDONED = of("OUTBOX_RESOLVED_ABANDONED");

    public ProcessTimelineEventCode {
        value = DurableIdentifiers.requireIdentity(value, "timeline event code", 64);
        if (!value.matches("[A-Z][A-Z0-9_]*")) {
            throw new IllegalArgumentException("timeline event code must be uppercase alphanumeric with underscores");
        }
    }

    private static ProcessTimelineEventCode of(String value) {
        return new ProcessTimelineEventCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
