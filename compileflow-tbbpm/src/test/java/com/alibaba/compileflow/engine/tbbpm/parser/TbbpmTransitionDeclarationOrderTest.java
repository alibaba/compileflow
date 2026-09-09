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
package com.alibaba.compileflow.engine.tbbpm.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.tbbpm.writer.TbbpmXmlWriter;
import com.alibaba.compileflow.engine.tbbpm.model.ExclusiveNode;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModel;
import com.alibaba.compileflow.engine.tbbpm.model.Transition;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Verifies that transition declaration order is the only routing order contract.
 *
 * @author yusu
 */
class TbbpmTransitionDeclarationOrderTest {
    private static ExclusiveNode getExclusive(TbbpmModel model, String nodeId) {
        ExclusiveNode exclusive = (ExclusiveNode) model.getNode(nodeId);
        assertThat(exclusive).withFailMessage("exclusive node must exist").isNotNull();
        return exclusive;
    }

    private static TbbpmModel parse(String xml) {
        return TbbpmXmlParser
            .getInstance()
            .parse(FlowSource.of("test.declaration-order", xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static String exclusiveXml() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="test.declaration-order" name="Declaration Order">
                <start id="start" name="Start" g="0,0,32,32">
                    <transition to="decision"/>
                </start>
                <exclusive id="decision" name="Decision" g="80,0,100,40">
                    <transition to="first" condition="value &gt; 0"/>
                    <transition to="second"/>
                </exclusive>
                <end id="first" name="First" g="220,0,32,32"/>
                <end id="second" name="Second" g="220,80,32,32"/>
            </bpm>
            """;
    }

    @Test
    void declarationOrderSurvivesRoundTrip() {
        TbbpmModel first = parse(exclusiveXml());
        assertThat(getExclusive(first, "decision").getOutgoingTransitions())
            .extracting(Transition::getTarget)
            .containsExactly("first", "second");

        ByteArrayOutputStream output = (ByteArrayOutputStream) TbbpmXmlWriter.getInstance().write(first);
        String written = output.toString(StandardCharsets.UTF_8);
        assertThat(written).doesNotContain("priority=");

        TbbpmModel second = parse(written);
        assertThat(getExclusive(second, "decision").getOutgoingTransitions())
            .extracting(Transition::getTarget)
            .containsExactly("first", "second");
    }

    @Test
    void editorGeometryDoesNotChangeExecutableTransitions() {
        String xml = exclusiveXml().replace(
            "<transition to=\"decision\"/>",
            "<transition to=\"decision\"><?workbench-edge {\"sourcePort\":\"right\",\"targetPort\":\"left\"}?></transition>"
        );
        TbbpmModel model = parse(xml);
        assertThat(model.getNode("start").getOutgoingTransitions())
            .extracting(Transition::getTarget)
            .containsExactly("decision");
        assertThat(getExclusive(model, "decision").getOutgoingTransitions())
            .extracting(Transition::getTarget)
            .containsExactly("first", "second");
    }

    @Test
    void rejectsRemovedPriorityAttribute() {
        assertThatThrownBy(() -> parse(exclusiveXml().replace("to=\"first\"", "to=\"first\" priority=\"1\"")))
            .isInstanceOf(RuntimeException.class);
    }
}
