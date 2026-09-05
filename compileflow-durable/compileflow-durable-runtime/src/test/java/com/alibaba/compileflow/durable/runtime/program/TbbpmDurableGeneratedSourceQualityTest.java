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
package com.alibaba.compileflow.durable.runtime.program;

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.durable.runtime.machine.DurableMachinePlan;
import com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.tbbpm.parser.TbbpmXmlParser;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModel;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TbbpmDurableGeneratedSourceQualityTest {
    @TempDir
    Path debugDirectory;

    @Test
    void emitsDeterministicReadableAndCompilableProcessSpecialization() {
        TbbpmModel model = parse(flow());
        DurableJavaProgramCompiler compiler = new DurableJavaProgramCompiler(JavaDiagnosticsConfig.defaults());
        DurableMachinePlan machinePlan = DurableCompilerTestSupport.lower(model);

        String first = compiler.generateSource(machinePlan);
        String second = compiler.generateSource(DurableCompilerTestSupport.lower(parse(flow())));

        assertThat(second).isEqualTo(first);
        assertThat(first)
            .startsWith("package com.alibaba.compileflow.generated.durable.process;")
            .contains("@Generated(value = \"compileflow.durable-java/v1\"")
            .contains("MachineTurnScheduler.Selection _cf$selection")
            .contains("private FrontierStepResult advanceFrontier(")
            .contains("private static final class _cf$FrontierCursor")
            .contains("private FrontierStepResult _cf$stepChunk0(")
            .contains("List<OccurrenceKey> _cf$consumed")
            .contains("// Node: routeDecision (ChooseOne)")
            .containsPattern("expressionRouteDecisionTransition0_[0-9a-f]{12}\\(")
            .contains("// Node: reminder (Timer)")
            .contains("private Map<String, Object> output() {\n    return Map.of();")
            .doesNotContain("Map<FrontierId, OccurrenceResult> _cf$results")
            .doesNotContain("private FrontierStepResult run(")
            .doesNotContain("_cf$RunState")
            .doesNotContain("_cf$runChunk")
            .doesNotContain("_condition0(")
            .doesNotContain("_timer0(")
            .doesNotContain("String selected = null", "selected == null", "output(_cf$state)")
            .doesNotContain("List<com.alibaba.compileflow.durable.runtime.kernel.OccurrenceKey>")
            .doesNotContain("package com.alibaba.compileflow.durable.generated;")
            .doesNotEndWith("\n\n}\n");
        assertThat(first.lines()).allMatch(line -> line.equals(line.stripTrailing()));

        assertThat(compiler.compile(machinePlan, getClass().getClassLoader())).isNotNull();
    }

    @Test
    void emitsReadableInterpretedExpressionSource() throws Exception {
        DurableMachinePlan machinePlan = DurableCompilerTestSupport.lower(parse(flow()));
        DurableInterpretedProgramCompiler compiler = new DurableInterpretedProgramCompiler(JavaDiagnosticsConfig
            .builder()
            .debugOutputDirectory(debugDirectory)
            .build());

        assertThat(compiler.compile(machinePlan, getClass().getClassLoader())).isNotNull();

        Path sourcePath;
        try (var sources = Files.walk(debugDirectory.resolve("source"))) {
            sourcePath = sources
                .filter(path -> path.toString().endsWith(".java"))
                .findFirst()
                .orElseThrow();
        }
        String source = Files.readString(sourcePath);
        assertThat(source)
            .startsWith("package com.alibaba.compileflow.generated.durable.expression;")
            .contains("@Generated(value = \"compileflow.durable-expressions/v1\")")
            .contains("        Boolean route = (Boolean) arguments[0];")
            .contains("        return route;")
            .doesNotContain("return (", "java.lang.Boolean", "com.alibaba.compileflow.durable.generated")
            .doesNotEndWith("\n\n}\n");
        assertThat(source.lines().mapToInt(String::length).max().orElseThrow()).isLessThanOrEqualTo(120);
    }

    @Test
    void makesInvisibleFormatCharactersVisibleInGeneratedSource() {
        String bidiOverride = Character.toString(0x202e);
        TbbpmModel model = parse(flow()
            .replace("<var name=\"route\"",
                    "<var name=\"marker\" dataType=\"java.lang.String\" inOutType=\"inner\" defaultValue=\""
                    + bidiOverride + "\"/>\n  <var name=\"route\""));
        DurableJavaProgramCompiler compiler = new DurableJavaProgramCompiler(JavaDiagnosticsConfig.defaults());
        DurableMachinePlan machinePlan = DurableCompilerTestSupport.lower(model);

        String source = compiler.generateSource(machinePlan);

        assertThat(source).doesNotContain(bidiOverride).contains("\\u202E");
        assertThat(compiler.compile(machinePlan, getClass().getClassLoader())).isNotNull();
    }

    @Test
    void compilesWhenTheParentLoaderDoesNotExposePackageDirectories() {
        DurableMachinePlan machinePlan = DurableCompilerTestSupport.lower(parse(flow()));
        ClassLoader parent = getClass().getClassLoader();
        ClassLoader directoryBlindLoader = new ClassLoader(parent) {
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                if (name.equals("com/alibaba/compileflow/durable/runtime/kernel")
                        || name.equals("com/alibaba/compileflow/durable/runtime/kernel/")
                        || name.equals("com/alibaba/compileflow/durable/runtime/program")
                        || name.equals("com/alibaba/compileflow/durable/runtime/program/")) {
                    return Collections.emptyEnumeration();
                }
                return super.getResources(name);
            }
        };

        assertThat(new DurableJavaProgramCompiler().compile(machinePlan, directoryBlindLoader)).isNotNull();
    }

    private static TbbpmModel parse(String xml) {
        return TbbpmXmlParser
            .getInstance()
            .parse(FlowSource.of("generated-source-quality", xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static String flow() {
        return """
            <bpm code="durable.generated.quality">
              <var name="route" dataType="java.lang.Boolean" inOutType="param"/>
              <start id="start" g="0,0,32,32"><transition to="routeDecision"/></start>
              <exclusive id="routeDecision" g="60,0,100,40">
                <transition to="reminder" condition="route"/>
                <transition to="done"/>
              </exclusive>
              <timerTask id="reminder" duration="PT1M" g="200,0,100,40">
                <transition to="done"/>
              </timerTask>
              <end id="done" g="340,0,32,32"/>
            </bpm>
            """;
    }
}
