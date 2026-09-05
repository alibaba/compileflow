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
package com.alibaba.compileflow.engine.core.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessCallDepthExceptionTest {
    @Test
    void preservesCallerSafeStateAcrossSerialization() throws Exception {
        ProcessCallDepthException original = new ProcessCallDepthException(List.of("parent.flow", "child.flow"), 1);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }

        ProcessCallDepthException restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (ProcessCallDepthException) input.readObject();
        }

        assertThat(restored.getErrorCode()).isEqualTo(ErrorCode.CF_EXEC_013);
        assertThat(restored.getProcessCallPath()).containsExactly("parent.flow", "child.flow");
        assertThat(restored.getMaxProcessCallDepth()).isEqualTo(1);
        assertThat(restored.getMessage())
            .isEqualTo("Process call depth 2 exceeds configured maximum 1: " + "parent.flow -> child.flow");
        assertThatThrownBy(() -> restored.getProcessCallPath().add("other.flow"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void requiresCanonicalProcessCodes() {
        assertThatThrownBy(() -> new ProcessCallDepthException(List.of("\u00a0"), 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("code must not be blank");
        assertThatThrownBy(() -> new ProcessCallDepthException(List.of("parent\u202eflow"), 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("code must not contain control characters or Unicode format characters");
    }
}
