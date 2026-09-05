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

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.bpmn.semantic.BpmnSemanticFrontend;
import com.alibaba.compileflow.engine.bpmn.parser.BpmnXmlParser;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.tbbpm.semantic.TbbpmSemanticFrontend;
import com.alibaba.compileflow.engine.tbbpm.parser.TbbpmXmlParser;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CrossFormatSemanticGoldenTest {
    @Test
    void equivalentTbbpmAndBpmnSourcesProduceOneCanonicalProcessMeaning() {
        ProcessSemanticPlan tbbpm =
                new TbbpmSemanticFrontend()
            .compile(TbbpmXmlParser.getInstance().parse(source("cross-format.bpm", TBBPM)));
        ProcessSemanticPlan bpmn =
                new BpmnSemanticFrontend()
            .compile(BpmnXmlParser.getInstance().parse(source("cross-format.bpmn", BPMN)));

        assertThat(bpmn.canonicalForm()).isEqualTo(tbbpm.canonicalForm());
        assertThat(bpmn.getDigest())
            .isEqualTo(tbbpm.getDigest())
            .isEqualTo("4b0412b664b6f91d7d010a8d0bad7ac60ec3c3736c6dce35fde6d335a221c1f7");
        assertThat(bpmn.requireNode("choice").outgoingTransitions())
            .extracting(ProcessSemanticPlan.TransitionPlan::targetId, ProcessSemanticPlan.TransitionPlan::condition,
                    ProcessSemanticPlan.TransitionPlan::defaultFlow)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("accepted", "approved", false),
                    org.assertj.core.groups.Tuple.tuple("rejected", null, true));
    }

    private static FlowSource source(String name, String value) {
        return FlowSource.of(name, value.getBytes(StandardCharsets.UTF_8));
    }

    private static final String TBBPM =
            """
        <bpm code="semantic.cross-format" name="Cross-format semantic golden">
          <var name="approved" dataType="java.lang.Boolean" inOutType="param"/>
          <var name="result" dataType="java.lang.Integer" defaultValue="0" inOutType="return"/>
          <start id="start" g="0,0,32,32"><transition to="calculate"/></start>
          <autoTask id="calculate" g="80,0,100,40">
            <action type="java" class="java.lang.Math" method="abs">
                <input target="value" dataType="java.lang.Integer" defaultValue="-7"/>
                <output dataType="java.lang.Integer" target="result"/>

            </action>
            <transition to="choice"/>
          </autoTask>
          <exclusive id="choice" g="220,0,48,48">
            <transition to="accepted" condition="approved"/>
            <transition to="rejected"/>
          </exclusive>
          <end id="accepted" g="340,0,32,32"/>
          <end id="rejected" g="340,80,32,32"/>
        </bpm>
        """;
    private static final String BPMN =
            """
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xmlns:cf="http://www.compileflow.org"
                     targetNamespace="urn:compileflow:test">
          <process id="semantic.cross-format" name="Cross-format semantic golden" isExecutable="true">
            <extensionElements>
              <cf:var name="approved" dataType="java.lang.Boolean" inOutType="param"/>
              <cf:var name="result" dataType="java.lang.Integer" defaultValue="0" inOutType="return"/>
            </extensionElements>
            <startEvent id="start"/>
            <serviceTask id="calculate">
              <extensionElements>
                <cf:action type="java" class="java.lang.Math" method="abs">
                    <cf:input target="value" dataType="java.lang.Integer" defaultValue="-7"/>
                    <cf:output dataType="java.lang.Integer" target="result"/>

                </cf:action>
              </extensionElements>
            </serviceTask>
            <exclusiveGateway id="choice" default="to_rejected"/>
            <endEvent id="accepted"/>
            <endEvent id="rejected"/>
            <sequenceFlow id="to_calculate" sourceRef="start" targetRef="calculate"/>
            <sequenceFlow id="to_choice" sourceRef="calculate" targetRef="choice"/>
            <sequenceFlow id="to_accepted" sourceRef="choice" targetRef="accepted">
              <conditionExpression xsi:type="tFormalExpression" language="java">approved</conditionExpression>
            </sequenceFlow>
            <sequenceFlow id="to_rejected" sourceRef="choice" targetRef="rejected"/>
          </process>
        </definitions>
        """;
}
