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
package com.alibaba.compileflow.engine.core.source;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Simple directed graph used during flow model conversion analysis.
 *
 * @author yusu
 */
final class DirectedGraph<V> {
    private final Map<V, Set<V>> outgoingVertices = new LinkedHashMap<>();

    void addEdge(V from, V to) {
        outgoingVertices
            .computeIfAbsent(from, ignored -> new LinkedHashSet<>())
            .add(to);
        outgoingVertices.computeIfAbsent(to, ignored -> new LinkedHashSet<>());
    }

    List<V> findCycle() {
        Map<V, VisitState> visitStates = new HashMap<>();
        Map<V, Integer> pathIndexes = new HashMap<>();
        List<V> path = new ArrayList<>();
        for (V vertex : outgoingVertices.keySet()) {
            List<V> cycle = findCycleFrom(vertex, visitStates, pathIndexes, path);
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }
        return Collections.emptyList();
    }

    private List<V> findCycleFrom(V vertex, Map<V, VisitState> visitStates, Map<V, Integer> pathIndexes, List<V> path) {
        if (visitStates.containsKey(vertex)) {
            return Collections.emptyList();
        }

        visitStates.put(vertex, VisitState.VISITING);
        pathIndexes.put(vertex, path.size());
        path.add(vertex);
        Deque<TraversalFrame<V>> traversal = new ArrayDeque<>();
        traversal.push(new TraversalFrame<>(vertex, outgoingVertices.get(vertex).iterator()));

        while (!traversal.isEmpty()) {
            TraversalFrame<V> frame = traversal.peek();
            if (frame.outgoing().hasNext()) {
                V outgoingVertex = frame.outgoing().next();
                VisitState state = visitStates.get(outgoingVertex);
                if (state == VisitState.VISITING) {
                    return List.copyOf(path.subList(pathIndexes.get(outgoingVertex), path.size()));
                }
                if (state == VisitState.VISITED) {
                    continue;
                }

                visitStates.put(outgoingVertex, VisitState.VISITING);
                pathIndexes.put(outgoingVertex, path.size());
                path.add(outgoingVertex);
                traversal.push(new TraversalFrame<>(outgoingVertex, outgoingVertices.get(outgoingVertex).iterator()));
                continue;
            }

            traversal.pop();
            V completed = frame.vertex();
            path.remove(path.size() - 1);
            pathIndexes.remove(completed);
            visitStates.put(completed, VisitState.VISITED);
        }
        return Collections.emptyList();
    }

    private enum VisitState {
        VISITING,
        VISITED
    }

    private record TraversalFrame<V>(V vertex, Iterator<V> outgoing) {}
}
