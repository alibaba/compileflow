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
import com.alibaba.compileflow.durable.api.validation.DurableNumbers;
import java.time.Instant;
import java.util.Objects;

/**
 * Operator-visible state of one active Effect occurrence.
 *
 * @param effectId         stable Effect occurrence identity
 * @param frontierId       stable logical continuation frontier
 * @param elementId        Process element that owns this occurrence
 * @param status           current Effect lifecycle
 * @param occurrenceSequence owning occurrence
 * @param dispatchAttempts committed dispatch claims
 * @param reconcileAttempts committed reconciliation claims
 * @param readinessCode   current pre-invocation readiness backoff reason, or {@code null}
 * @param nextAttemptAt    next automatic attempt eligibility, or {@code null}
 * @param reviewRequiredAt time automatic reconciliation yielded to an operator, or {@code null}
 * @param reviewReason     stable redacted review category, or {@code null}
 * @param reviewRevision   current operator-review authority revision
 * @author yusu
 */
public record ActiveEffect(String effectId, String frontierId, String elementId, EffectExecutionStatus status,
        long occurrenceSequence, int dispatchAttempts, int reconcileAttempts, EffectReadinessCode readinessCode,
        Instant nextAttemptAt, Instant reviewRequiredAt, String reviewReason, long reviewRevision)
        implements ActiveWork {
    public ActiveEffect {
        effectId = DurableIdentifiers.requireIdentity(effectId, "effectId", 96);
        frontierId = DurableIdentifiers.requireIdentity(frontierId, "frontierId", 128);
        elementId = DurableIdentifiers.requireIdentity(elementId, "elementId", 128);
        status = Objects.requireNonNull(status, "status");
        occurrenceSequence = DurableNumbers.requirePositive(occurrenceSequence, "occurrenceSequence");
        dispatchAttempts = DurableNumbers.requireRange(dispatchAttempts, 0, Integer.MAX_VALUE, "dispatchAttempts");
        reconcileAttempts = DurableNumbers.requireRange(reconcileAttempts, 0, Integer.MAX_VALUE, "reconcileAttempts");
        reviewReason = DurableIdentifiers.optionalHumanText(reviewReason, "reviewReason", 2_048);
        reviewRevision = DurableNumbers.requireNonNegative(reviewRevision, "reviewRevision");
        if ((reviewRequiredAt == null) != (reviewReason == null)) {
            throw new IllegalArgumentException("reviewRequiredAt and reviewReason must be present together");
        }
        if (readinessCode != null && status != EffectExecutionStatus.PENDING && status != EffectExecutionStatus.UNKNOWN) {
            throw new IllegalArgumentException("readinessCode is valid only for pending or uncertain Effect");
        }
        if (readinessCode != null && reviewRequiredAt != null) {
            throw new IllegalArgumentException("readiness backoff and operator review must not coexist");
        }
        if (reviewRequiredAt != null && status != EffectExecutionStatus.UNKNOWN) {
            throw new IllegalArgumentException("operator review is valid only for UNKNOWN Effect");
        }
        if (reviewRequiredAt != null && reviewRevision == 0) {
            throw new IllegalArgumentException("operator review requires a positive reviewRevision");
        }
        if (reviewRequiredAt != null && nextAttemptAt != null) {
            throw new IllegalArgumentException("operator review must not expose an automatic next attempt");
        }
        if (status != EffectExecutionStatus.PENDING && status != EffectExecutionStatus.UNKNOWN && nextAttemptAt != null) {
            throw new IllegalArgumentException("nextAttemptAt is valid only for pending or uncertain Effect");
        }
    }

    /**
     * Whether automatic reconciliation has yielded to an operator.
     */
    public boolean reviewRequired() {
        return reviewRequiredAt != null;
    }
}
