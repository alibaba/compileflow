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
package com.alibaba.compileflow.engine.core.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.core.model.FlowModel;
import com.alibaba.compileflow.engine.core.model.TransitionNode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AbstractFlowModelReaderTest {
    @Test
    void rejectsNullParserResultWithTypedSystemFailure() {
        byte[] source = "<process/>".getBytes(StandardCharsets.UTF_8);
        ProcessDefinitionSnapshot definition = ProcessDefinitionSnapshot.of("default", "order", "v1", source, "test");
        NullFlowModelReader reader = new NullFlowModelReader();

        assertThatExceptionOfType(CompileFlowException.class)
            .isThrownBy(() -> reader.read(definition))
            .satisfies(failure -> {
                assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.CF_COMPILE_001);
                assertThat(failure.getMessage()).contains("code=order", "modelType=TBBPM");
            });
    }

    private static final class NullFlowModelReader extends AbstractFlowModelReader<FlowModel<TransitionNode<?>>> {
        @Override
        protected ProcessModelType getFlowModelType() {
            return ProcessModelType.TBBPM;
        }

        @Override
        protected FlowModel<TransitionNode<?>> read(FlowSource flowStreamSource) {
            return null;
        }
    }
}
