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

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class DirectedGraphTest {
    @Test
    void returnsTheExactCycleWithoutDownstreamVertices() {
        DirectedGraph<String> graph = new DirectedGraph<>();
        graph.addEdge("start", "a");
        graph.addEdge("a", "b");
        graph.addEdge("b", "a");
        graph.addEdge("b", "after-cycle");

        assertThat(graph.findCycle()).containsExactly("a", "b");
    }

    @Test
    void returnsEmptyWhenGraphIsAcyclic() {
        DirectedGraph<String> graph = new DirectedGraph<>();
        graph.addEdge("start", "middle");
        graph.addEdge("middle", "end");

        assertThat(graph.findCycle()).isEmpty();
    }

    @Test
    void detectsSelfLoop() {
        DirectedGraph<String> graph = new DirectedGraph<>();
        graph.addEdge("self", "self");

        assertThat(graph.findCycle()).containsExactly("self");
    }

    @Test
    void handlesDeepAcyclicGraphsWithoutUsingTheThreadStack() {
        DirectedGraph<Integer> graph = new DirectedGraph<>();
        for (int i = 0; i < 20_000; i++) {
            graph.addEdge(i, i + 1);
        }

        assertThat(graph.findCycle()).isEmpty();
    }
}
