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
package com.alibaba.compileflow.engine.test.feature.routing;

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.test.support.config.ProcessEngineTestConfiguration;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessEngineTestFactory;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProcessEngineTestConfiguration.class)
public class DebugVersionRoutingTest {
    @Test
    void returnsDeclaredValueFromInlineDefinition() {
        ProcessEngine engine = ProcessEngineTestFactory.createTbbpm();

        String flowXml =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<bpm code=\"debug.return\" name=\"debug\">\n"
                + "    <var name=\"result\" dataType=\"java.lang.String\" inOutType=\"return\"/>\n" + "\n"
                + "    <start id=\"start\" name=\"Start\" g=\"50,50,32,32\">\n" + "        <transition to=\"calc\"/>\n"
                + "    </start>\n" + "\n" + "    <scriptTask id=\"calc\" name=\"Calc\" g=\"150,40,88,48\">\n"
                + "        <action type=\"script\" language=\"java\">\n"
                + "            <output target=\"result\" dataType=\"java.lang.String\"/>\n"
                + "            <code><![CDATA[return \"test_value\";]]></code>\n" + "        </action>\n"
                + "        <transition to=\"end\"/>\n" + "    </scriptTask>\n" + "\n"
                + "    <end id=\"end\" name=\"End\" g=\"300,50,32,32\"/>\n" + "</bpm>";

        ProcessDefinition.Inline definition = ProcessDefinition.inline("debug.return", flowXml);

        try {
            engine.runtime().warmUp(definition);

            ProcessResult<Map<String, Object>> result = engine.execute(definition, new HashMap<>());
            // Semantic assertions (observable and attributable):
            // 1) Execution succeeds
            // 2) Return data is non-null and contains result
            // 3) Result value is deterministic
            assertThat(result.isSuccess()).as("Execution should succeed: %s", result.getError()).isTrue();
            assertThat(result.getOutput()).as("Result data should not be null").isNotNull();
            assertThat(result.getOutput().containsKey("result")).as("Result data should contain key 'result'").isTrue();
            assertThat(result.getOutput().get("result")).as("Return variable should contain test_value").isEqualTo(
                    "test_value");
        } finally {
            engine.close();
        }
    }
}
