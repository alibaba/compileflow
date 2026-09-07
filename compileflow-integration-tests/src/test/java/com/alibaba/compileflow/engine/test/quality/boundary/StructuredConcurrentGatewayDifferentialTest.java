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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StructuredConcurrentGatewayDifferentialTest {
    private static final long SEED = 20_260_809L;
    private static final int GRAPH_COUNT = 8;
    private static final int ROUTES_PER_GRAPH = 16;

    @Test
    void generatedMixedGatewayGraphsMatchIndependentTokenOracle() {
        Random random = new Random(SEED);
        try (ProcessEngine engine = ProcessEngineTestFactory.create()) {
            for (int graphIndex = 0; graphIndex < GRAPH_COUNT; graphIndex++) {
                ModelBuilder model = new ModelBuilder("test.gateway.concurrent-differential." + graphIndex);
                Region root = model.region(random, 0, 4);
                ProcessDefinition definition =
                        ProcessDefinition.inline(ProcessModelType.TBBPM, model.code, model.render(root));

                String javaSource = engine.tooling().generateJavaCode(definition);
                for (String field : root.fields()) {
                    assertThat(occurrences(javaSource, model.marker(field)))
                        .as("seed=%s graph=%s field=%s", SEED, graphIndex, field)
                        .isEqualTo(1);
                }

                for (int route = 0; route < ROUTES_PER_GRAPH; route++) {
                    ProcessResult<Map<String, Object>> result = engine.execute(definition, Map.of("route", route));

                    assertThat(result.isSuccess())
                        .as("seed=%s graph=%s route=%s error=%s", SEED, graphIndex, route, result.getError())
                        .isTrue();
                    Set<String> active = root.oracle().activeFields(route);
                    for (String field : root.fields()) {
                        assertThat(result.getOutput().get(field))
                            .as("seed=%s graph=%s route=%s field=%s", SEED, graphIndex, route, field)
                            .isEqualTo(active.contains(field) ? model.marker(field) : null);
                    }
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

    private record Region(String entryId, String exitId, TokenOracle oracle, Set<String> fields) {}

    @FunctionalInterface
    private interface TokenOracle {
        Set<String> activeFields(int route);
    }

    private static final class ModelBuilder {
        private final String code;
        private final Map<String, NodeSpec> nodes = new LinkedHashMap<>();
        private final Set<String> fields = new LinkedHashSet<>();
        private int nextId;
        private int nextBit;

        private ModelBuilder(String code) {
            this.code = code;
        }

        private Region region(Random random, int depth, int maxDepth) {
            if (depth >= maxDepth) {
                return task("leaf");
            }
            int shape = random.nextInt(100);
            if (shape < 32) {
                return task("leaf");
            }
            if (shape < 57) {
                return exclusive(random, depth, maxDepth);
            }
            if (shape < 79) {
                return parallel(random, depth, maxDepth);
            }
            return inclusive(random, depth, maxDepth);
        }

        private Region task(String prefix) {
            NodeSpec task = add(NodeKind.TASK, prefix);
            String field = "value" + nextId++;
            task.outputField = field;
            fields.add(field);
            return new Region(task.id, task.id, ignored -> Set.of(field), Set.of(field));
        }

        private Region exclusive(Random random, int depth, int maxDepth) {
            int mask = nextMask();
            NodeSpec split = add(NodeKind.EXCLUSIVE, "choice");
            Region left = region(random, depth + 1, maxDepth);
            Region right = region(random, depth + 1, maxDepth);
            Region merge = task("choiceMerge");

            split.transitions.add(new TransitionSpec(left.entryId, "(route &amp; " + mask + ") == 0"));
            split.transitions.add(new TransitionSpec(right.entryId, null));
            connect(left.exitId, merge.entryId);
            connect(right.exitId, merge.entryId);

            Set<String> regionFields = union(left.fields, right.fields, merge.fields);
            return new Region(split.id, merge.exitId,
                    route -> union((route & mask) == 0
                            ? left.oracle.activeFields(route)
                            : right.oracle.activeFields(route), merge.oracle.activeFields(route)), regionFields);
        }

        private Region parallel(Random random, int depth, int maxDepth) {
            NodeSpec split = add(NodeKind.PARALLEL, "parallelFork");
            Region first = region(random, depth + 1, maxDepth);
            Region second = region(random, depth + 1, maxDepth);
            NodeSpec join = add(NodeKind.PARALLEL, "parallelJoin");
            Region merge = task("parallelMerge");

            split.transitions.add(new TransitionSpec(first.entryId, null));
            split.transitions.add(new TransitionSpec(second.entryId, null));
            connect(first.exitId, join.id);
            connect(second.exitId, join.id);
            connect(join.id, merge.entryId);

            Set<String> regionFields = union(first.fields, second.fields, merge.fields);
            return new Region(split.id, merge.exitId,
                    route -> union(first.oracle.activeFields(route), second.oracle.activeFields(route),
                            merge.oracle.activeFields(route)), regionFields);
        }

        private Region inclusive(Random random, int depth, int maxDepth) {
            int firstMask = nextMask();
            int secondMask = nextMask();
            NodeSpec split = add(NodeKind.INCLUSIVE, "inclusiveFork");
            Region first = region(random, depth + 1, maxDepth);
            Region second = region(random, depth + 1, maxDepth);
            Region fallback = region(random, depth + 1, maxDepth);
            NodeSpec join = add(NodeKind.INCLUSIVE, "inclusiveJoin");
            Region merge = task("inclusiveMerge");

            split.transitions.add(new TransitionSpec(first.entryId, "(route &amp; " + firstMask + ") != 0"));
            split.transitions.add(new TransitionSpec(second.entryId, "(route &amp; " + secondMask + ") != 0"));
            split.transitions.add(new TransitionSpec(fallback.entryId, null));
            connect(first.exitId, join.id);
            connect(second.exitId, join.id);
            connect(fallback.exitId, join.id);
            connect(join.id, merge.entryId);

            Set<String> regionFields = union(first.fields, second.fields, fallback.fields, merge.fields);
            return new Region(split.id, merge.exitId, route -> {
                Set<String> active = new LinkedHashSet<>();
                if ((route & firstMask) != 0) {
                    active.addAll(first.oracle.activeFields(route));
                }
                if ((route & secondMask) != 0) {
                    active.addAll(second.oracle.activeFields(route));
                }
                if (active.isEmpty()) {
                    active.addAll(fallback.oracle.activeFields(route));
                }
                active.addAll(merge.oracle.activeFields(route));
                return Set.copyOf(active);
            }, regionFields);
        }

        private int nextMask() {
            return 1 << nextBit++ % 4;
        }

        private NodeSpec add(NodeKind kind, String prefix) {
            NodeSpec node = new NodeSpec(prefix + nextId++, kind);
            nodes.put(node.id, node);
            return node;
        }

        private void connect(String sourceId, String targetId) {
            nodes.get(sourceId).transitions.add(new TransitionSpec(targetId, null));
        }

        private String marker(String field) {
            return "marker{" + field + "}";
        }

        private String render(Region root) {
            StringBuilder xml = new StringBuilder();
            xml
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<bpm code=\"")
                .append(code)
                .append("\">\n")
                .append("  <var name=\"route\" dataType=\"java.lang.Integer\" inOutType=\"param\"/>\n");
            for (String field : fields) {
                xml.append("  <var name=\"").append(field).append(
                        "\" dataType=\"java.lang.String\" inOutType=\"return\"/>\n");
            }
            xml.append("  <start id=\"start\" g=\"0,0,32,32\"><transition to=\"").append(root.entryId).append(
                    "\"/></start>\n");
            connect(root.exitId, "end");
            nodes.values().forEach(node -> node.appendTo(xml, this));
            xml.append("  <end id=\"end\" g=\"0,0,32,32\"/>\n</bpm>");
            return xml.toString();
        }
    }

    private enum NodeKind {
        EXCLUSIVE("exclusive"),
        PARALLEL("parallel"),
        INCLUSIVE("inclusive"),
        TASK("autoTask");
        private final String element;

        NodeKind(String element) {
            this.element = element;
        }
    }

    private static final class NodeSpec {
        private final String id;
        private final NodeKind kind;
        private final List<TransitionSpec> transitions = new ArrayList<>();
        private String outputField;

        private NodeSpec(String id, NodeKind kind) {
            this.id = id;
            this.kind = kind;
        }

        private void appendTo(StringBuilder xml, ModelBuilder model) {
            xml.append("  <").append(kind.element).append(" id=\"").append(id).append("\" g=\"0,0,80,48\">\n");
            if (outputField != null) {
                xml
                    .append("    <action type=\"java\" ")
                    .append("class=\"com.alibaba.compileflow.engine.test.support.mocks.MockJavaService\" ")
                    .append("method=\"processStep\">\n")
                    .append("      <input target=\"input\" dataType=\"java.lang.String\" defaultValue=\"")
                    .append(model.marker(outputField))
                    .append("\"/>\n")
                    .append("      <output dataType=\"java.lang.String\" target=\"")
                    .append(outputField)
                    .append("\"/>\n")
                    .append("    </action>\n");
            }
            for (TransitionSpec transition : transitions) {
                xml.append("    <transition to=\"").append(transition.targetId).append('"');
                if (transition.expression != null) {
                    xml.append(" condition=\"").append(transition.expression).append('"');
                }
                xml.append("/>\n");
            }
            xml.append("  </").append(kind.element).append(">\n");
        }
    }

    @SafeVarargs
    private static <T> Set<T> union(Set<T>... sources) {
        Set<T> result = new LinkedHashSet<>();
        for (Set<T> source : sources) {
            result.addAll(source);
        }
        return Set.copyOf(result);
    }

    private record TransitionSpec(String targetId, String expression) {}
}
