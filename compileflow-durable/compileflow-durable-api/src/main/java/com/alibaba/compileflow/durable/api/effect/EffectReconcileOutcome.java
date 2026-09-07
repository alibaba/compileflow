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
package com.alibaba.compileflow.durable.api.effect;

/**
 * Closed result of an Effect policy's recovery-only reconcile Action.
 *
 * <p>This is not an Effect handler, target identity, node, or registry key.
 * ProcessEngine execution returns an Effect Action's existing business result directly;
 * only a Process-owned reconcile Action returns this evidence value.</p>
 *
 * @param <R> original Effect Action return type
 *
 * @author yusu
 */
public sealed interface EffectReconcileOutcome<R>
        permits EffectReconcileOutcome.ConfirmedResult, EffectReconcileOutcome.ConfirmedNotExecuted,
        EffectReconcileOutcome.StillUnknown {
    /**
     * The external operation completed and produced the original result.
     *
     * <p>The value retains its exact business type, including concrete collection types.
     * Like an Action return value, it must not be mutated after return. The runtime maps and
     * serializes it against the original Action output contract before persisting it.</p>
     *
     * @param value confirmed business result
     * @param <R> original Effect Action return type
     */
    record ConfirmedResult<R>(R value) implements EffectReconcileOutcome<R> {
        @Override
        public String toString() {
            return "ConfirmedResult[value=<redacted>]";
        }
    }

    /**
     * The external operation definitely did not execute.
     *
     * @param <R> original Effect Action return type
     */
    record ConfirmedNotExecuted<R>() implements EffectReconcileOutcome<R> {}

    /**
     * The external outcome remains uncertain.
     *
     * @param <R> original Effect Action return type
     */
    record StillUnknown<R>() implements EffectReconcileOutcome<R> {}

    static <R> EffectReconcileOutcome<R> confirmed(R value) {
        return new ConfirmedResult<>(value);
    }

    static <R> EffectReconcileOutcome<R> notExecuted() {
        return new ConfirmedNotExecuted<>();
    }

    static <R> EffectReconcileOutcome<R> unknown() {
        return new StillUnknown<>();
    }
}
