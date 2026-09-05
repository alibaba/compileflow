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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Shared deterministic scheduling prelude for every compiled Durable Machine Turn.
 *
 * @author yusu
 */
public final class MachineTurnScheduler {
    private MachineTurnScheduler() {
    }

    /**
     * Validates offered occurrence results and selects the next executable frontier in persisted
     * round-robin order.
     *
     * <p>This method deliberately owns only process-independent scheduling. The generated program
     * remains a direct, inspectable specialization of the Process model.</p>
     */
    public static Selection select(ContinuationSnapshot continuation, List<OccurrenceResult> availableResults,
            int maxActiveIterations) {
        ContinuationSnapshot snapshot = Objects.requireNonNull(continuation, "continuation");
        Map<FrontierId, OccurrenceResult> results = indexResults(availableResults);
        FrontierSnapshot selected = null;
        OccurrenceResult selectedResult = null;
        for (FrontierSnapshot candidate : snapshot.frontiers()) {
            OccurrenceResult result = results.remove(candidate.frontierId());
            if (result != null && !candidate.resumePoint().isAfterElement()) {
                throw new IllegalArgumentException("Occurrence result targets a non-waiting frontier");
            }
            boolean controllerReady = MultiInstanceFrontierOperations.canIssue(snapshot, candidate, maxActiveIterations);
            if (selected == null && (candidate.runnable() || controllerReady || result != null)) {
                selected = candidate;
                selectedResult = result;
            }
        }
        if (!results.isEmpty()) {
            throw new IllegalArgumentException("Occurrence result targets an unknown frontier");
        }
        if (selected == null) {
            throw new IllegalArgumentException("Continuation has no executable frontier");
        }
        ArrayList<FrontierSnapshot> remaining = new ArrayList<>(snapshot.frontiers());
        if (!remaining.remove(selected)) {
            throw new IllegalStateException("Selected frontier is absent from continuation");
        }
        return new Selection(selected, selectedResult, remaining);
    }

    private static Map<FrontierId, OccurrenceResult> indexResults(List<OccurrenceResult> availableResults) {
        LinkedHashMap<FrontierId, OccurrenceResult> results = new LinkedHashMap<>();
        for (OccurrenceResult result : Objects.requireNonNull(availableResults, "availableResults")) {
            OccurrenceResult candidate = Objects.requireNonNull(result, "availableResults element");
            OccurrenceResult previous = results.putIfAbsent(candidate.frontierId(), candidate);
            if (previous != null) {
                throw new IllegalArgumentException("One frontier cannot consume multiple occurrence results");
            }
        }
        return results;
    }

    /**
     * Validated input for the process-specific portion of one generated Machine Turn.
     */
    public record Selection(FrontierSnapshot frontier, OccurrenceResult occurrenceResult,
            List<FrontierSnapshot> remainingFrontiers) {
        public Selection {
            frontier = Objects.requireNonNull(frontier, "frontier");
            remainingFrontiers = List.copyOf(Objects.requireNonNull(remainingFrontiers, "remainingFrontiers"));
        }
    }
}
