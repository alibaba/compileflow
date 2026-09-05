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
package com.alibaba.compileflow.durable.runtime.kernel;

/**
 * Shared operational budget for one Machine Turn; it never changes Process results.
 *
 * <p>The worker passes one thread-confined instance through every root and child invocation in
 * the Turn so generated and interpreted programs consume the same bound.
 *
 * @author yusu
 */
public final class TurnBudget {
    public static final int ABSOLUTE_MAX_STEPS = 1_000_000;
    private final int maxSteps;
    private final int maxActiveIterations;
    private int remainingSteps;

    public TurnBudget(int maxSteps, int maxActiveIterations) {
        if (maxSteps <= 0 || maxSteps > ABSOLUTE_MAX_STEPS) {
            throw new IllegalArgumentException("maxSteps must be in [1, " + ABSOLUTE_MAX_STEPS + ']');
        }
        if (maxActiveIterations <= 0 || maxActiveIterations > MultiInstanceState.ABSOLUTE_MAX_ACTIVE) {
            throw new IllegalArgumentException(
                    "maxActiveIterations must be in [1, " + MultiInstanceState.ABSOLUTE_MAX_ACTIVE + ']');
        }
        this.maxSteps = maxSteps;
        this.maxActiveIterations = maxActiveIterations;
        remainingSteps = maxSteps;
    }

    public int maxSteps() {
        return maxSteps;
    }

    public int maxActiveIterations() {
        return maxActiveIterations;
    }

    public boolean hasRemainingSteps() {
        return remainingSteps > 0;
    }

    public boolean tryConsumeStep() {
        if (remainingSteps == 0) {
            return false;
        }
        remainingSteps--;
        return true;
    }

    public static TurnBudget defaults() {
        return new TurnBudget(10_000, 32);
    }
}
