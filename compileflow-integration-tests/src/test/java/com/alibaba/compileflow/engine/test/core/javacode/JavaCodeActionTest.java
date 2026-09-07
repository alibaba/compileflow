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
package com.alibaba.compileflow.engine.test.core.javacode;

import com.alibaba.compileflow.engine.ProcessModelType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessEngineTestFactory;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JavaCodeActionTest {
    @Test
    void executesAJavaCodeTaskThroughExplicitTypedBindings() {
        try (ProcessEngine engine = ProcessEngineTestFactory.create()) {
            ProcessResult<Map<String, Object>> result = engine.execute(ProcessDefinition.classpath(ProcessModelType.TBBPM,
                            "bpm.java-code." + "javaCodeSum", "bpm.java-code.javaCodeSum".replace(".", "/") + ".bpm"),
                    Map.of("inputA", 25, "inputB", 35));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("result", 60);
        }
    }

    @Test
    void preservesActionFailureForInvalidBusinessInput() {
        try (ProcessEngine engine = ProcessEngineTestFactory.create()) {
            ProcessResult<Map<String, Object>> result = engine.execute(ProcessDefinition.classpath(ProcessModelType.TBBPM,
                            "bpm.java-code." + "javaCodeCalculator",
                            "bpm.java-code.javaCodeCalculator".replace(".", "/") + ".bpm"),
                    Map.of("inputA", 10, "inputB", 0, "op", "/"));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getError().getMessage()).isEqualTo("Script execution error");
        }
    }

    @Test
    void rejectsInvalidJavaCodeDuringCompilation() {
        ProcessDefinition definition = ProcessDefinition.inline(ProcessModelType.TBBPM, "test.java-code.invalid",
                """
            <bpm code="test.java-code.invalid">
              <var name="result" dataType="java.lang.Integer" inOutType="return"/>
              <start id="start" g="0,0,32,32"><transition to="task"/></start>
              <scriptTask id="task" g="64,0,100,40">
                <action type="script" language="java">
                    <output dataType="java.lang.Integer" target="result"/>
                    <code>return missingSymbol;</code>

                </action>
                <transition to="end"/>
              </scriptTask>
              <end id="end" g="200,0,32,32"/>
            </bpm>
            """);

        try (ProcessEngine engine = ProcessEngineTestFactory.create()) {
            assertThatThrownBy(() -> engine.tooling().generateJavaCode(definition)).hasMessageContaining(
                    "missingSymbol");
        }
    }
}
