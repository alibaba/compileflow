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
import com.alibaba.compileflow.durable.api.validation.DurablePayload;
import com.alibaba.compileflow.durable.api.model.AuditPrincipal;
import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import java.util.Map;
import java.util.Objects;

/**
 * Audited, revision-fenced resolution of one uncertain Effect occurrence.
 *
 * @param runId owning Run identity
 * @param effectId exact Effect occurrence identity
 * @param expectedReviewRevision expected current review revision
 * @param decision operator resolution decision
 * @param result confirmed result, only for successful resolution
 * @param actor authenticated operator
 * @param reason bounded audit reason
 * @param auditContextId optional external audit correlation
 *
 * @author yusu
 */
public record ResolveEffectCommand(ProcessRunId runId, String effectId, long expectedReviewRevision,
        EffectResolutionDecision decision, Map<String, ?> result, AuditPrincipal actor, String reason,
        String auditContextId) {
    public ResolveEffectCommand {
        runId = Objects.requireNonNull(runId, "runId");
        effectId = DurableIdentifiers.requireCanonicalUuid(effectId, "effectId");
        expectedReviewRevision = DurableNumbers.requirePositive(expectedReviewRevision, "expectedReviewRevision");
        decision = Objects.requireNonNull(decision, "decision");
        result = DurablePayload.immutablePayload(result, "result");
        if (decision != EffectResolutionDecision.CONFIRM_SUCCEEDED && !result.isEmpty()) {
            throw new IllegalArgumentException("result is allowed only for CONFIRM_SUCCEEDED");
        }
        actor = Objects.requireNonNull(actor, "actor");
        reason = DurableIdentifiers.requireHumanText(reason, "reason", DurableIdentifiers.MAX_REASON_CHARACTERS);
        auditContextId = DurableIdentifiers.optionalIdentity(auditContextId, "auditContextId",
                DurableIdentifiers.MAX_AUDIT_CONTEXT_ID_CHARACTERS);
    }
}
