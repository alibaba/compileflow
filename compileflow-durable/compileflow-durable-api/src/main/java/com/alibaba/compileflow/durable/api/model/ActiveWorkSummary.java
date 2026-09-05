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

import com.alibaba.compileflow.durable.api.validation.DurableNumbers;

/**
 * Bounded active-authority summary embedded in a Run.
 *
 * <p>Individual occurrences are exposed only through the paged operator query.</p>
 *
 * @param waits active external waits
 * @param timers active timers
 * @param effects active Effects
 * @param runningEffects currently executing Effects
 * @param unknownEffects Effects with uncertain outcomes
 * @param reviewRequiredEffects uncertain Effects requiring operator review
 *
 * @author yusu
 */
public record ActiveWorkSummary(int waits, int timers, int effects, int runningEffects, int unknownEffects,
        int reviewRequiredEffects) {
    public static final ActiveWorkSummary NONE = new ActiveWorkSummary(0, 0, 0, 0, 0, 0);

    public ActiveWorkSummary {
        waits = nonNegative(waits, "waits");
        timers = nonNegative(timers, "timers");
        effects = nonNegative(effects, "effects");
        runningEffects = nonNegative(runningEffects, "runningEffects");
        unknownEffects = nonNegative(unknownEffects, "unknownEffects");
        reviewRequiredEffects = nonNegative(reviewRequiredEffects, "reviewRequiredEffects");
        if (runningEffects > effects || unknownEffects > effects || reviewRequiredEffects > unknownEffects) {
            throw new IllegalArgumentException("Effect work summary is inconsistent");
        }
    }

    public int total() {
        return Math.addExact(Math.addExact(waits, timers), effects);
    }

    public boolean isEmpty() {
        return total() == 0;
    }

    private static int nonNegative(int value, String name) {
        return DurableNumbers.requireRange(value, 0, Integer.MAX_VALUE, name);
    }
}
