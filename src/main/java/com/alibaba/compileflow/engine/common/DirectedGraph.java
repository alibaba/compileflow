/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.common;

import org.apache.commons.collections4.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Used to detect whether the process has circular dependencies, for more information:
 * https://en.wikipedia.org/wiki/Directed_graph
 *
 * @author yusu
 */
public class DirectedGraph<V> {

    private Map<V, Node<V>> neighbors = new HashMap<>();

    public void add(V vertex) {
        if (neighbors.containsKey(vertex)) {
            return;
        }
        neighbors.put(vertex, new Node<>());
    }

    public boolean contains(V vertex) {
        return neighbors.containsKey(vertex);
    }

    public void add(Edge<V> edge) {
        V from = edge.getFrom();
        V to = edge.getTo();
        add(from);
        add(to);
        neighbors.get(from).addOutgoing(to);
        neighbors.get(to).addIncoming(from);
    }

    public boolean remove(Edge<V> edge) {
        V from = edge.getFrom();
        V to = edge.getTo();
        if (!contains(from) || !contains(to)) {
            return false;
        }
        neighbors.get(from).removeOutgoing(to);
        neighbors.get(to).removeIncoming(from);
        return true;
    }

    public Map<V, Integer> inDegree() {
        return neighbors.entrySet().stream().collect(
            Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getIncomings().size()));
    }

    public Map<V, Integer> outDegree() {
        return neighbors.entrySet().stream().collect(
            Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getOutgoings().size()));
    }

    public List<V> topSort() {
        Map<V, Integer> inDegree = inDegree();
        Stack<V> zeroInDegreeVertices = new Stack<>();
        inDegree.entrySet().stream().filter(e -> e.getValue() == 0).forEach(e -> zeroInDegreeVertices.push(e.getKey()));

        List<V> result = new ArrayList<>();
        while (CollectionUtils.isNotEmpty(zeroInDegreeVertices)) {
            V v = zeroInDegreeVertices.pop();
            result.add(v);
            for (V neighbor : neighbors.get(v).getOutgoings()) {
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                if (inDegree.get(neighbor) == 0) {
                    zeroInDegreeVertices.push(neighbor);
                }
            }
        }

        return result;
    }

    public List<V> findCyclicVertexList() {
        List<V> result = topSort();
        if (result.size() != neighbors.size()) {
            return neighbors.keySet().stream().filter(v -> !result.contains(v)).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    public static final class Node<V> {
        private Set<V> incomings;
        private Set<V> outgoings;

        public Node() {
            this.incomings = new HashSet<>();
            this.outgoings = new HashSet<>();
        }

        public Set<V> getIncomings() {
            return incomings;
        }

        public Set<V> getOutgoings() {
            return outgoings;
        }

        public void addIncoming(V vetex) {
            incomings.add(vetex);
        }

        public void addOutgoing(V vetex) {
            outgoings.add(vetex);
        }

        public void removeIncoming(V vetex) {
            incomings.remove(vetex);
        }

        public void removeOutgoing(V vetex) {
            outgoings.remove(vetex);
        }
    }

    public static final class Edge<V> {
        private V from;
        private V to;

        public static <V> Edge of(V from, V to) {
            Edge edge = new Edge<V>();
            edge.from = from;
            edge.to = to;
            return edge;
        }

        public V getFrom() {
            return from;
        }

        public V getTo() {
            return to;
        }
    }

}
