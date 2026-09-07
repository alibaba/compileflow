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
package com.alibaba.compileflow.engine.test.feature.runtime;

import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessDefinition;
import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.ProcessRuntimeManager;
import com.alibaba.compileflow.engine.ProcessToolingService;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import com.alibaba.compileflow.engine.test.support.config.ProcessEngineTestConfiguration;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessEngineTestFactory;
import com.alibaba.compileflow.engine.test.support.mocks.KtvService;
import com.google.common.io.Resources;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProcessEngineTestConfiguration.class)
@DisplayName("Process Engine Runtime Warm-up Integration Tests")
@Tag("integration")
public class ProcessRuntimeWarmUpIntegrationTest {
    @Autowired
    private KtvService ktvService;

    @Nested
    @DisplayName("A. BPMN20 exact-definition warm-up")
    class Bpmn20WarmUpTests {
        private ProcessEngine engine;
        private ProcessToolingService tooling;
        private ProcessRuntimeManager admin;

        @BeforeEach
        void setUp() {
            engine = ProcessEngineFactory.create(ProcessEngineTestFactory
                .builder()
                .componentResolver(componentResolver("ktvService", ktvService))
                .build());
            tooling = engine.tooling();
            admin = engine.runtime();
        }

        @AfterEach
        void tearDown() throws Exception {
            if (engine != null) {
                engine.close();
            }
        }

        @Test
        @DisplayName("should warm and execute a changed exact definition")
        void shouldWarmAndExecuteChangedDefinition() throws IOException {
            String code = "bpmn20.ktv.ktvExample";

            Map<String, Object> ctx = new HashMap<>();
            ctx.put("pList", Arrays.asList("u1", "u2"));
            // 1) Execute V1 (resource file default content)
            ProcessResult<Map<String, Object>> v1 = engine.execute(ProcessDefinition.classpath(ProcessModelType.BPMN,
                            code, code.replace(".", "/") + ".bpmn"), ctx);
            assertThat(v1.isSuccess()).as("V1 execution should succeed: %s", v1.getError()).isTrue();
            assertThat(v1.getOutput()).as("V1 data should not be null").isNotNull();
            assertThat(v1.getOutput().get("price")).as("V1 price should be 2 * 27 * 0.9 = 54").isEqualTo(54);
            // 2) Read original BPMN20 and construct V2 (replace calculatePrice with
            // calculatePriceForHotDeploy)
            String resourcePath = code.replace('.', '/') + ".bpmn";
            String v1Xml = Resources.toString(Resources.getResource(resourcePath), StandardCharsets.UTF_8);
            String v2Xml = v1Xml.replace("method=\"calculatePrice\"", "method=\"calculatePriceForHotDeploy\"");
            assertThat(v2Xml).as("V2 xml must differ from V1 to change digest").isNotEqualTo(v1Xml);
            // 3) Engine-level observability: tooling generated code must reflect action binding
            // changes
            String v2Java = tooling.generateJavaCode(ProcessDefinition.inline(ProcessModelType.BPMN, code, v2Xml));
            assertThat(v2Java).as("V2 java code should be generated").isNotNull();
            assertThat(v2Java).as("Generated code should contain updated method binding").contains(
                    "calculatePriceForHotDeploy");

            ProcessDefinition.Inline v2Definition = ProcessDefinition.inline(ProcessModelType.BPMN, code, v2Xml);
            // 4) Load the changed definition into the local runtime cache.
            admin.warmUp(v2Definition);
            // 5) Execute V2: same input produces distinguishable output
            // (MockJavaService.calculatePriceForHotDeploy -> 1000*num, then 0.9 discount)
            ProcessResult<Map<String, Object>> v2 = engine.execute(v2Definition, ctx);
            assertThat(v2.isSuccess()).as("V2 execution should succeed: %s", v2.getError()).isTrue();
            assertThat(v2.getOutput()).as("V2 data should not be null").isNotNull();
            assertThat(v2.getOutput().get("price")).as("V2 price should be 2 * 1000 * 0.9 = 1800").isEqualTo(1800);
        }
    }

    @Nested
    @DisplayName("B. TBBPM runtime warm-up")
    class TbbpmWarmUpTests {
        private ProcessEngine engine;

        @BeforeEach
        void setUp() {
            engine = ProcessEngineFactory.create(ProcessEngineTestFactory
                .builder()
                .componentResolver(componentResolver("ktvService", ktvService))
                .build());
        }

        @AfterEach
        void tearDown() throws Exception {
            if (engine != null) {
                engine.close();
            }
        }

        @Test
        @DisplayName("repeated warm-up should be idempotent")
        void repeatedWarmUpShouldBeIdempotent() {
            String code = "bpm.ktv.ktvExample";

            Map<String, Object> ctx = new HashMap<>();
            ctx.put("pList", Arrays.asList("u1", "u2"));

            ProcessDefinition.Classpath definition =
                    ProcessDefinition.classpath(ProcessModelType.TBBPM, code, "bpm/ktv/ktvExample.bpm");
            engine.runtime().warmUp(definition);
            ProcessResult<Map<String, Object>> result1 = engine.execute(ProcessDefinition.classpath(ProcessModelType.TBBPM,
                            code, code.replace(".", "/") + ".bpm"), ctx);
            // Repeated local loading is idempotent.
            engine.runtime().warmUp(definition);
            ProcessResult<Map<String, Object>> result2 = engine.execute(ProcessDefinition.classpath(ProcessModelType.TBBPM,
                            code, code.replace(".", "/") + ".bpm"), ctx);
            // Semantic assertions:
            // 1) Both executions must succeed
            // 2) Both should produce price (proves flow is executable and return var mechanism
            // works)
            assertThat(result1.isSuccess()).as("First execution should succeed: %s", result1.getError()).isTrue();
            assertThat(result1.getOutput()).as("First execute data should not be null").isNotNull();
            assertThat(result1.getOutput()).containsKey("price");

            assertThat(result2.isSuccess()).as("Second execution should succeed: %s", result2.getError()).isTrue();
            assertThat(result2.getOutput()).as("Second execute data should not be null").isNotNull();
            assertThat(result2.getOutput()).containsKey("price");
        }

        @Test
        @DisplayName("warm-up should preserve exact definition identity")
        void warmUpShouldPreserveExactDefinitionIdentity() {
            String code = "bpm.ktv.ktvExample";

            String v1 = readResource("/bpm/ktv/ktvExample.bpm");
            assertThat(v1).as("Resource file should be found").isNotNull();
            // Replace action method so the output changes after hot reload
            String v2 = v1.replace("method=\"calculatePrice\"", "method=\"calculatePriceForHotDeploy\"");

            Map<String, Object> ctx = new HashMap<>();
            ctx.put("pList", Arrays.asList("u1", "u2"));

            ProcessDefinition.Classpath v1Definition =
                    ProcessDefinition.classpath(ProcessModelType.TBBPM, code, "bpm/ktv/ktvExample.bpm");
            engine.runtime().warmUp(v1Definition);
            ProcessResult<Map<String, Object>> r1 = engine.execute(ProcessDefinition.classpath(ProcessModelType.TBBPM,
                            code, code.replace(".", "/") + ".bpm"), ctx);

            ProcessDefinition.Inline v2Definition = ProcessDefinition.inline(ProcessModelType.TBBPM, code, v2);
            engine.runtime().warmUp(v2Definition);
            ProcessResult<Map<String, Object>> r2 = engine.execute(v2Definition, ctx);
            ProcessResult<Map<String, Object>> codeAfterWarmUp = engine.execute(ProcessDefinition.classpath(ProcessModelType.TBBPM,
                            code, code.replace(".", "/") + ".bpm"), ctx);

            assertThat(r1.isSuccess()).as("V1 execute failed: %s", r1.getError()).isTrue();
            assertThat(r2.isSuccess()).as("V2 execute failed: %s", r2.getError()).isTrue();
            assertThat(codeAfterWarmUp.isSuccess())
                .as("Code execute after V2 warm-up failed: %s", codeAfterWarmUp.getError())
                .isTrue();
            // V1 should calculate price as 60 (MockJavaService.calculatePrice: 30*num)
            assertThat(r1.getOutput()).containsEntry("price", 60);
            // V2 should calculate price as 1800 (MockJavaService.calculatePriceForHotDeploy:
            // 1000*num * 0.9)
            assertThat(r2.getOutput()).containsEntry("price", 1800);
            // Warm-up must not rebind the public Code identity to the explicit V2 definition.
            assertThat(codeAfterWarmUp.getOutput()).containsEntry("price", 60);

            assertThat(r1.getExecution().getTraceId()).isNotBlank();
            assertThat(r1.getExecution().getProcessCode()).isEqualTo(code);
            assertThat(r1.getExecution().getProcessVersion()).isNull();
        }

        private String readResource(String path) {
            try (InputStream in = ProcessRuntimeWarmUpIntegrationTest.class.getResourceAsStream(path)) {
                if (in == null) {
                    return null;
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    out.write(buf, 0, n);
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            } catch (IOException failure) {
                return null;
            }
        }
    }

    private static ProcessComponentResolver componentResolver(String componentName, Object component) {
        return new ProcessComponentResolver() {
            @Override
            public <T> T resolve(String name, Class<T> requiredType) {
                if (!componentName.equals(name) || !requiredType.isInstance(component)) {
                    throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_003,
                            "Process component is not available: " + name);
                }
                return requiredType.cast(component);
            }
        };
    }
}
