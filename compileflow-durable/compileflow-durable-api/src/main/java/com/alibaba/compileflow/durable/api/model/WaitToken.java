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
 * Opaque one-shot authority for one exact committed Wait occurrence.
 *
 * <p>This value belongs to the materialized Wait occurrence, not to a Worker attempt, lease, or
 * Outbox delivery attempt. Every retry for that occurrence carries the same token until the Wait is
 * completed, expired, or cancelled. It is a bearer completion capability, not a business identity.
 * Integrations must preserve it until completion through opaque transport, protected storage, or
 * an external-ID mapping. It must not be logged, used as a metric label, exposed in a browser-visible
 * URL, or copied to third-party metadata.</p>
 *
 * @param value opaque bearer capability
 *
 * @author yusu
 */
public record WaitToken(String value) {
    public WaitToken {
        value = DurableIdentifiers.requireOpaqueToken(value);
    }

    @Override
    public String toString() {
        return "WaitToken[<redacted>]";
    }
}
