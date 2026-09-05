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
package com.alibaba.compileflow.engine.core.runtime.execution;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Establishes deterministic loop inputs at activity entry.
 *
 * @author yusu
 */
public final class LoopSemantics {
    private LoopSemantics() {
    }

    /**
     * Copies an iterable or array before the first iteration.
     *
     * <p>The snapshot fixes the number and order of iterations. Mutating the
     * source container from the loop body therefore cannot add, remove, or
     * reorder instances that are already being executed.</p>
     *
     * @param source      iterable or array loop input
     * @param loopId      model node identifier used for diagnostics
     * @param itemType declared foreach item type
     * @param <T>      generated foreach item type
     * @return immutable snapshot that preserves source order and null items
     */
    public static <T> List<T> snapshot(Object source, String loopId, Class<T> itemType) {
        if (source == null) {
            throw invalid(loopId, "collection is null", null);
        }
        Objects.requireNonNull(itemType, "itemType");

        List<T> snapshot = new ArrayList<>();
        if (source instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                addItem(snapshot, item, itemType, loopId);
            }
            return Collections.unmodifiableList(snapshot);
        }
        if (source.getClass().isArray()) {
            int length = Array.getLength(source);
            for (int index = 0; index < length; index++) {
                addItem(snapshot, Array.get(source, index), itemType, loopId);
            }
            return Collections.unmodifiableList(snapshot);
        }

        throw invalid(loopId, "collection must be an Iterable or array, found " + source.getClass().getName(),
                source.getClass().getName());
    }

    /**
     * Rejects a while-loop iteration that would exceed its declared bound.
     *
     * @param loopId              model node identifier used for diagnostics
     * @param completedIterations number of iterations already completed
     * @param maxIterations       maximum number of permitted iterations
     */
    public static void requireIterationAllowed(String loopId, int completedIterations, int maxIterations) {
        if (maxIterations <= 0) {
            throw new IllegalArgumentException("maxIterations must be positive");
        }
        if (completedIterations < 0) {
            throw new IllegalArgumentException("completedIterations must not be negative");
        }
        if (completedIterations < maxIterations) {
            return;
        }

        CompileFlowException failure = invalid(loopId,
                "maxIterations=" + maxIterations + " exceeded after " + completedIterations + " completed iterations",
                null);
        failure.withContext("completedIterations", completedIterations);
        failure.withContext("maxIterations", maxIterations);
        throw failure;
    }

    @SuppressWarnings("unchecked")
    private static <T> void addItem(List<T> snapshot, Object item, Class<T> itemType, String loopId) {
        if (item != null && !itemType.isInstance(item)) {
            CompileFlowException failure = invalid(loopId,
                    "item at index " + snapshot.size() + " must be " + itemType.getName() + ", found " + item
                        .getClass()
                        .getName(), item.getClass().getName());
            failure.withContext("itemIndex", snapshot.size());
            failure.withContext("expectedItemType", itemType.getName());
            throw failure;
        }
        snapshot.add((T) item);
    }

    private static CompileFlowException invalid(String loopId, String detail, String sourceType) {
        CompileFlowException failure =
                new CompileFlowException(ErrorCode.CF_EXEC_008,
                        "Invalid loop input for node '" + loopId + "': " + detail);
        failure.withContext("loopId", loopId);
        if (sourceType != null) {
            failure.withContext("sourceType", sourceType);
        }
        return failure;
    }
}
