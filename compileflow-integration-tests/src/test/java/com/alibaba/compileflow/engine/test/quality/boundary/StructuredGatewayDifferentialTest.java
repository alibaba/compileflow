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
package com.alibaba.compileflow.engine.test.quality.boundary;

import com.alibaba.compileflow.engine.ProcessModelType;
import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessEngineTestFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class StructuredGatewayDifferentialTest {
    private static final long SEED = 20_260_808L;
    private static final int GRAPH_COUNT = 12;
    private static final int ROUTES_PER_GRAPH = 16;

    @Test
    void generatedStructuredGraphsMatchIndependentTokenOracle() {
        Random random = new Random(SEED);
        try (ProcessEngine engine = ProcessEngineTestFactory.create()) {
            for (int graphIndex = 0; graphIndex < GRAPH_COUNT; graphIndex++) {
                ModelBuilder model = new ModelBuilder("test.gateway.differential." + graphIndex);
                Region root = model.choice(random, 0, 5);
                ProcessDefinition definition =
                        ProcessDefinition.inline(ProcessModelType.TBBPM, model.code, model.render(root));

                String javaSource = engine.tooling().generateJavaCode(definition);
                for (String mergeMarker : root.mergeMarkers()) {
                    assertThat(occurrences(javaSource, mergeMarker))
                        .as("seed=%s graph=%s marker=%s", SEED, graphIndex, mergeMarker)
                        .isEqualTo(1);
                }

                for (int route = 0; route < ROUTES_PER_GRAPH; route++) {
                    ProcessResult<Map<String, Object>> result = engine.execute(definition, Map.of("route", route));

                    assertThat(result.isSuccess())
                        .as("seed=%s graph=%s route=%s error=%s", SEED, graphIndex, route, result.getError())
                        .isTrue();
                    assertThat(result.getOutput().get("trace"))
                        .as("seed=%s graph=%s route=%s", SEED, graphIndex, route)
                        .isEqualTo(root.trace(route));
                }
            }
        }
    }

    private static int occurrences(String value, String fragment) {
        int count = 0;
        for (int offset = 0; (offset = value.indexOf(fragment, offset)) >= 0; offset += fragment.length()) {
            count++;
        }
        return count;
    }

    private record Region(String entryId, String exitId, TraceOracle oracle, List<String> mergeMarkers) {
        String trace(int route) {
            return oracle.trace(route);
        }
    }

    @FunctionalInterface
    private interface TraceOracle {
        String trace(int route);
    }

    private static final class ModelBuilder {
        private final String code;
        private final Map<String, NodeSpec> nodes = new LinkedHashMap<>();
        private int nextId;
        private int nextBit;

        private ModelBuilder(String code) {
            this.code = code;
        }

        private Region choice(Random random, int depth, int maxDepth) {
            int bit = nextBit++ % Integer.SIZE;
            NodeSpec split = add(NodeKind.EXCLUSIVE, "split");
            Region left = region(random, depth + 1, maxDepth);
            Region right = region(random, depth + 1, maxDepth);
            NodeSpec merge = add(NodeKind.SCRIPT_TASK, "merge");
            String mergeMarker = "|" + merge.id;
            merge.marker = mergeMarker;

            split.transitions.add(new TransitionSpec(left.entryId, "(route &amp; " + (1 << bit) + ") == 0"));
            split.transitions.add(new TransitionSpec(right.entryId, null));
            nodes.get(left.exitId).transitions.add(new TransitionSpec(merge.id, null));
            nodes.get(right.exitId).transitions.add(new TransitionSpec(merge.id, null));

            List<String> mergeMarkers = new ArrayList<>();
            mergeMarkers.addAll(left.mergeMarkers);
            mergeMarkers.addAll(right.mergeMarkers);
            mergeMarkers.add(mergeMarker);
            return new Region(split.id, merge.id,
                    route -> ((route & (1 << bit)) == 0 ? left.trace(route) : right.trace(route)) + mergeMarker,
                    List.copyOf(mergeMarkers));
        }

        private Region region(Random random, int depth, int maxDepth) {
            if (depth < maxDepth && random.nextInt(100) < 58) {
                return choice(random, depth, maxDepth);
            }
            NodeSpec leaf = add(NodeKind.SCRIPT_TASK, "leaf");
            String marker = "|" + leaf.id;
            leaf.marker = marker;
            return new Region(leaf.id, leaf.id, route -> marker, List.of());
        }

        private NodeSpec add(NodeKind kind, String prefix) {
            NodeSpec node = new NodeSpec(prefix + nextId++, kind);
            nodes.put(node.id, node);
            return node;
        }

        private String render(Region root) {
            StringBuilder xml = new StringBuilder();
            xml
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<bpm code=\"")
                .append(code)
                .append("\">\n")
                .append("  <var name=\"route\" dataType=\"java.lang.Integer\" inOutType=\"param\"/>\n")
                .append("  <var name=\"trace\" dataType=\"java.lang.String\" inOutType=\"return\"/>\n")
                .append("  <start id=\"start\" g=\"0,0,32,32\"><transition to=\"")
                .append(root.entryId)
                .append("\"/></start>\n");
            nodes.get(root.exitId).transitions.add(new TransitionSpec("end", null));
            nodes.values().forEach(node -> node.appendTo(xml));
            xml.append("  <end id=\"end\" g=\"0,0,32,32\"/>\n</bpm>");
            return xml.toString();
        }
    }

    private enum NodeKind {
        EXCLUSIVE,
        SCRIPT_TASK
    }

    private static final class NodeSpec {
        private final String id;
        private final NodeKind kind;
        private final List<TransitionSpec> transitions = new ArrayList<>();
        private String marker;

        private NodeSpec(String id, NodeKind kind) {
            this.id = id;
            this.kind = kind;
        }

        private void appendTo(StringBuilder xml) {
            String element = kind == NodeKind.EXCLUSIVE ? "exclusive" : "scriptTask";
            xml.append("  <").append(element).append(" id=\"").append(id).append("\" g=\"0,0,80,48\">\n");
            if (marker != null) {
                xml
                    .append("    <action type=\"script\" language=\"java\">")
                    .append("<input source=\"trace\" target=\"trace\" dataType=\"java.lang.String\"/>")
                    .append("<output target=\"trace\" dataType=\"java.lang.String\"/>")
                    .append("<code><![CDATA[return (trace == null ? \"\" : trace) + \"")
                    .append(marker)
                    .append("\";]]></code></action>\n");
            }
            for (TransitionSpec transition : transitions) {
                xml.append("    <transition to=\"").append(transition.targetId).append('"');
                if (transition.expression != null) {
                    xml.append(" condition=\"").append(transition.expression).append('"');
                }
                xml.append("/>\n");
            }
            xml.append("  </").append(element).append(">\n");
        }
    }

    private record TransitionSpec(String targetId, String expression) {}
}
