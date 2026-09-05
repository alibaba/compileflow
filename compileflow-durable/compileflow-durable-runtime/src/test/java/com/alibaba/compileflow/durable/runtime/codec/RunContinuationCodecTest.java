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
package com.alibaba.compileflow.durable.runtime.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.durable.runtime.kernel.ContinuationSnapshot;
import com.alibaba.compileflow.durable.runtime.kernel.RunContinuation;
import com.alibaba.compileflow.durable.runtime.program.DurableProcessRuntime;
import com.alibaba.compileflow.durable.runtime.program.DurableCompilerTestSupport;
import com.alibaba.compileflow.durable.runtime.program.DurableJavaProgramCompiler;
import com.alibaba.compileflow.engine.ProcessDefinitionDigest;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.tbbpm.parser.TbbpmXmlParser;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RunContinuationCodecTest {
    @Test
    void roundTripsTheCurrentExactProcessGraph() {
        DurableProcessRuntime program = loadProgram();
        RunContinuation continuation = RunContinuation.start(program.processId(), ContinuationSnapshot.start(Map.of()),
                Map.of(new RunContinuation.ProcessCallSite(program.processId(), "payment"), program.processId()));
        RunContinuationCodec codec = new RunContinuationCodec();

        RunContinuation decoded = codec.decode(codec.encode(continuation, processId -> program), processId -> program);

        assertThat(decoded).isEqualTo(continuation);
    }

    @Test
    void rejectsAnUnknownFormatVersion() {
        DurableProcessRuntime program = loadProgram();
        RunContinuation continuation =
                RunContinuation.start(program.processId(), ContinuationSnapshot.start(Map.of()), Map.of());
        byte[] encoded = new RunContinuationCodec().encode(continuation, processId -> program);
        ByteBuffer.wrap(encoded).putInt(Integer.BYTES, 2);

        assertThatThrownBy(() -> new RunContinuationCodec().decode(encoded, processId -> program))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Run continuation format is unsupported");
    }

    @Test
    void rejectsMalformedUtf8Text() {
        DurableProcessRuntime program = loadProgram();
        RunContinuation continuation = RunContinuation.start(program.processId(), ContinuationSnapshot.start(Map.of()),
                Map.of(new RunContinuation.ProcessCallSite(program.processId(), "payment"), program.processId()));
        byte[] encoded = new RunContinuationCodec().encode(continuation, processId -> program);
        int callSiteIdOffset = Integer.BYTES * 3 + Long.BYTES * 2 + Integer.BYTES;
        encoded[callSiteIdOffset] = (byte) 0xc3;
        encoded[callSiteIdOffset + 1] = 0x28;

        assertThatThrownBy(() -> new RunContinuationCodec().decode(encoded, processId -> program))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Run continuation text must be valid UTF-8");
    }

    @Test
    void rejectsAProcessCallTargetThatTheResolverLoadsUnderTheWrongIdentity() {
        DurableProcessRuntime program = loadProgram();
        UUID missingProcessId = UUID.randomUUID();
        RunContinuation continuation = RunContinuation.start(program.processId(), ContinuationSnapshot.start(Map.of()),
                Map.of(new RunContinuation.ProcessCallSite(program.processId(), "payment"), missingProcessId));
        byte[] encoded = new RunContinuationCodec().encode(continuation, processId -> program);

        assertThatThrownBy(() -> new RunContinuationCodec().decode(encoded, processId -> program))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Loaded Process ID does not match the requested Process: " + missingProcessId);
    }

    private DurableProcessRuntime loadProgram() {
        String code = "codec.continuation";
        byte[] definition = ("""
            <bpm code="%s">
              <start id="start" g="0,0,32,32"><transition to="end"/></start>
              <end id="end" g="100,0,32,32"/>
            </bpm>
            """)
            .formatted(code)
            .getBytes(StandardCharsets.UTF_8);
        var model = TbbpmXmlParser.getInstance().parse(FlowSource.of(code, definition));
        var machinePlan = DurableCompilerTestSupport.lower(model);
        var program = new DurableJavaProgramCompiler(JavaDiagnosticsConfig.defaults())
            .compile(machinePlan, getClass().getClassLoader());
        String digest = ProcessDefinitionDigest.compute(ProcessModelType.TBBPM, code, definition);
        UUID processId = UUID.nameUUIDFromBytes((code + digest).getBytes(StandardCharsets.UTF_8));
        return new DurableProcessRuntime(processId, code, ProcessModelType.TBBPM, digest, machinePlan, program,
                new DurableValueSerializer(machinePlan), Map.of());
    }
}
