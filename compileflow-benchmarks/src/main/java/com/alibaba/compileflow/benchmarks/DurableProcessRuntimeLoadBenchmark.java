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
package com.alibaba.compileflow.benchmarks;

import com.alibaba.compileflow.durable.runtime.program.DurableProgram;
import com.alibaba.compileflow.durable.runtime.machine.DurableProcessCompiler;
import com.alibaba.compileflow.durable.runtime.program.DurableJavaProgramCompiler;
import com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.tbbpm.semantic.TbbpmSemanticFrontend;
import com.alibaba.compileflow.engine.tbbpm.parser.TbbpmXmlParser;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Cold-cache load cost for one exact Durable Process semantic program.
 *
 * <p>The measured operation includes UTF-8 parsing, validation, semantic-plan lowering,
 * generated Java, javac, class loading, and program construction. It excludes Store I/O,
 * JVM startup, compiler construction, Run admission, and PostgreSQL boundaries.</p>
 *
 * @author yusu
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 0)
@Measurement(iterations = 1, batchSize = 1)
@Fork(5)
public class DurableProcessRuntimeLoadBenchmark {
    @Param({"50", "200", "1000", "5000"})
    private int nodes;
    private DurableProcessCompiler processCompiler;
    private DurableJavaProgramCompiler compiler;
    private byte[] definition;

    /**
     * Builds the immutable benchmark definition outside the measured operation.
     */
    @Setup
    public void setup() {
        processCompiler = new DurableProcessCompiler();
        compiler = new DurableJavaProgramCompiler(JavaDiagnosticsConfig.defaults());
        definition = model(nodes).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Loads one uncached process runtime from its exact definition.
     *
     * @return compiled program, retained by JMH
     */
    @Benchmark
    public DurableProgram loadRuntime() {
        TbbpmModel model = TbbpmXmlParser.getInstance().parse(FlowSource.of("benchmark.durable.load", definition));
        return compiler.compile(processCompiler.lower(new TbbpmSemanticFrontend().compile(model)),
                getClass().getClassLoader());
    }

    private static String model(int nodeCount) {
        StringBuilder xml = new StringBuilder(nodeCount * 320);
        xml
            .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            .append("<bpm code=\"benchmark.durable.load\">\n")
            .append("  <start id=\"start\" g=\"0,0,32,32\"><transition to=\"task0\"/></start>\n");
        for (int index = 0; index < nodeCount; index++) {
            String target = index + 1 == nodeCount ? "end" : "task" + (index + 1);
            xml
                .append("  <autoTask id=\"task")
                .append(index)
                .append("\" g=\"0,0,80,40\">\n")
                .append(
                        "    <action type=\"java\" execution=\"replayable\" " + "class=\"java.util.Collections\" method=\"emptyList\"/>\n")
                .append("    <transition to=\"")
                .append(target)
                .append("\"/>\n")
                .append("  </autoTask>\n");
        }
        return xml.append("  <end id=\"end\" g=\"0,0,32,32\"/>\n</bpm>\n").toString();
    }
}
