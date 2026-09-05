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
package com.alibaba.compileflow.examples.springboot;

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessResult;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SampleApplicationTest {
    private final ProcessEngine processEngine;

    @Autowired
    SampleApplicationTest(ProcessEngine processEngine) {
        this.processEngine = processEngine;
    }

    @Test
    void startsAndExecutesTheSampleFlow() {
        ProcessDefinition source = ProcessDefinition.classpath("bpm.sample.hello", "flows/hello.bpm");

        ProcessResult<Map<String, Object>> result = processEngine.execute(source, Map.of("value", 40));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("result", 42);
    }
}
