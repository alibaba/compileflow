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

import com.alibaba.compileflow.durable.api.validation.DurableIdentifiers;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable input, completed output, and position for one active sequential foreach loop.
 *
 * @author yusu
 */
public final class ForEachFrame implements ScopeFrame {
    private final String loopId;
    private final int position;
    private final List<Object> snapshot;
    private final List<Object> completedResults;

    public ForEachFrame(String loopId, int position, List<?> snapshot) {
        this(loopId, position, snapshot, List.of());
    }

    public ForEachFrame(String loopId, int position, List<?> snapshot, List<?> completedResults) {
        this(position, requireText(loopId),
                DurableValueSnapshots.immutableList(Objects.requireNonNull(snapshot, "snapshot")),
                DurableValueSnapshots.immutableList(Objects.requireNonNull(completedResults, "completedResults")));
    }

    private ForEachFrame(int position, String loopId, List<Object> snapshot, List<Object> completedResults) {
        if (position < 0) {
            throw new IllegalArgumentException("foreach position must be non-negative");
        }
        if (position >= snapshot.size()) {
            throw new IllegalArgumentException("foreach position must address the immutable snapshot");
        }
        if (completedResults.size() > position + 1) {
            throw new IllegalArgumentException("foreach results cannot exceed completed iterations");
        }
        this.loopId = loopId;
        this.position = position;
        this.snapshot = snapshot;
        this.completedResults = completedResults;
    }

    private static String requireText(String value) {
        return DurableIdentifiers.requireIdentity(value, "loopId", 128);
    }

    @Override
    public String loopId() {
        return loopId;
    }

    @Override
    public int position() {
        return position;
    }

    public int snapshotSize() {
        return snapshot.size();
    }

    public Object currentValue() {
        return DurableValueSnapshots.detachedValue(snapshot.get(position));
    }

    public ForEachFrame advance() {
        return new ForEachFrame(position + 1, loopId, snapshot, completedResults);
    }

    public ForEachFrame recordResult(Object result) {
        if (completedResults.size() != position) {
            throw new IllegalStateException("foreach result has already been recorded for the current position");
        }
        List<Object> results = new ArrayList<>(completedResults);
        results.add(DurableValueSnapshots.detachedValue(result));
        return new ForEachFrame(position, loopId, snapshot, DurableValueSnapshots.immutableList(results));
    }

    public List<Object> completedResults() {
        return completedResults;
    }

    /**
     * Returns the immutable snapshot for the runtime-owned persistence codec.
     */
    public List<Object> snapshotForEncoding() {
        return snapshot;
    }

    @Override
    public boolean equals(Object candidate) {
        if (this == candidate) {
            return true;
        }
        if (!(candidate instanceof ForEachFrame other)) {
            return false;
        }
        return position == other.position && loopId.equals(other.loopId) && snapshot.equals(other.snapshot)
                && completedResults.equals(other.completedResults);
    }

    @Override
    public int hashCode() {
        return Objects.hash(loopId, position, snapshot, completedResults);
    }

    @Override
    public String toString() {
        return "ForEachFrame[loopId=" + loopId + ", position=" + position + ", snapshot=<redacted>]";
    }
}
