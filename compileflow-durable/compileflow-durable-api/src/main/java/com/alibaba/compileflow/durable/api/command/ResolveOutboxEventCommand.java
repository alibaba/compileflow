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
package com.alibaba.compileflow.durable.api.command;

import com.alibaba.compileflow.durable.api.validation.DurableIdentifiers;
import com.alibaba.compileflow.durable.api.validation.DurableNumbers;
import com.alibaba.compileflow.durable.api.model.AuditPrincipal;
import java.util.Objects;

/**
 * Audited, revision-fenced resolution of one unresolved or retriable Outbox delivery.
 *
 * <p>{@link OutboxResolutionDecision#RETRY} returns the delivery to pending publication. {@link
 * OutboxResolutionDecision#ABANDON} records an operator decision not to publish it. The command is
 * valid only when the referenced delivery is eligible for operator resolution.</p>
 *
 * @param eventId exact Outbox event identity
 * @param expectedRevision expected current event revision
 * @param decision operator resolution decision
 * @param actor authenticated operator
 * @param reason bounded audit reason
 * @param auditContextId optional external audit correlation
 *
 * @author yusu
 */
public record ResolveOutboxEventCommand(String eventId, long expectedRevision, OutboxResolutionDecision decision,
        AuditPrincipal actor, String reason, String auditContextId) {
    public ResolveOutboxEventCommand {
        eventId = DurableIdentifiers.requireCanonicalUuid(eventId, "eventId");
        expectedRevision = DurableNumbers.requirePositive(expectedRevision, "expectedRevision");
        decision = Objects.requireNonNull(decision, "decision");
        actor = Objects.requireNonNull(actor, "actor");
        reason = DurableIdentifiers.requireHumanText(reason, "reason", DurableIdentifiers.MAX_REASON_CHARACTERS);
        auditContextId = DurableIdentifiers.optionalIdentity(auditContextId, "auditContextId",
                DurableIdentifiers.MAX_AUDIT_CONTEXT_ID_CHARACTERS);
    }
}
