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
package com.alibaba.compileflow.engine.tbbpm;

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GeneratedMethodNameIsolationTest {
    private static final String CODE = "test.generated.method-names";
    private static final ProcessDefinition DEFINITION = ProcessDefinition.inline(CODE,
            """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpm code="test.generated.method-names" name="Method Name Isolation">
            <var name="left" dataType="java.lang.Boolean" inOutType="param"/>
            <var name="result" dataType="java.lang.Integer" inOutType="return"/>
            <start id="start" name="Start" g="0,0,32,32">
                <transition to="route"/>
            </start>
            <exclusive id="route" name="Route" g="80,0,100,40">
                <transition to="a-b" condition="left"/>
                <transition to="a_b"/>
            </exclusive>
            <scriptTask id="a-b" name="Hyphenated" g="220,0,100,40">
                <action type="script" language="java">
                        <output dataType="java.lang.Integer"
                             target="result"/>
                        <code>return 1;</code>

                </action>
                <transition to="end"/>
            </scriptTask>
            <scriptTask id="a_b" name="Underscored" g="220,80,100,40">
                <action type="script" language="java">
                        <output dataType="java.lang.Integer"
                             target="result"/>
                        <code>return 2;</code>

                </action>
                <transition to="end"/>
            </scriptTask>
            <end id="end" name="End" g="360,40,32,32"/>
        </bpm>
        """);

    @Test
    void keepsDistinctBranchesWhoseReadableJavaNamesWouldCollide() {
        ProcessEngineConfig config = ProcessEngineConfig.tbbpmBuilder().discoverPlugins(false).build();

        try (ProcessEngine engine = ProcessEngineFactory.create(config)) {
            ProcessResult<Map<String, Object>> left = engine.execute(DEFINITION, Map.of("left", true));
            ProcessResult<Map<String, Object>> right = engine.execute(DEFINITION, Map.of("left", false));

            assertThat(left.orElseThrow().get("result")).isEqualTo(1);
            assertThat(right.orElseThrow().get("result")).isEqualTo(2);
        }
    }
}
