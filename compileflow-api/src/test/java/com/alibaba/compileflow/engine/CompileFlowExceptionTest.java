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
package com.alibaba.compileflow.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CompileFlowExceptionTest {
    @Test
    void restoresUsableEmptyTransientContextAfterDeserialization() throws Exception {
        CompileFlowException original = new CompileFlowException(ErrorCode.CF_EXEC_008, "invalid decision");
        original.withContext("nonSerializable", new Object());

        CompileFlowException restored;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
            output.flush();
            try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                restored = (CompileFlowException) input.readObject();
            }
        }

        assertThat(restored.getErrorCode()).isEqualTo(ErrorCode.CF_EXEC_008);
        assertThat(restored.getMessage()).isEqualTo("invalid decision");
        assertThat(restored.getContext()).isEmpty();
        restored.withContext("attempt", 2);
        assertThat(restored.getContext()).containsEntry("attempt", 2);
        assertThatThrownBy(() -> restored.getContext().put("other", 3)).isInstanceOf(
                UnsupportedOperationException.class);
    }

    @Test
    void stringSummaryIncludesContextKeysButNotValues() {
        CompileFlowException exception = new CompileFlowException(ErrorCode.CF_EXEC_008, "invalid decision")
            .withContext("credential", "secret-token");

        assertThat(exception.toString()).contains("CF_EXEC_008", "contextKeys: [credential]").doesNotContain(
                "secret-token");
    }

    @Test
    void boundsAndSnapshotsDiagnosticContextWithoutChangingFailureFlow() {
        CompileFlowException exception = new CompileFlowException(ErrorCode.CF_EXEC_008, "invalid decision");
        List<String> mutable = new java.util.ArrayList<>(List.of("first"));
        exception
            .withContext(" branches ", mutable)
            .withContext("oversized", "x".repeat(2_049))
            .withContext("unsafe", new Object())
            .withContext("k".repeat(129), "ignored");
        mutable.add("later");

        assertThat(exception.getContext())
            .containsEntry("branches", List.of("first"))
            .containsEntry("oversized", "<omitted>")
            .containsEntry("unsafe", "<omitted>")
            .doesNotContainKey("k".repeat(129));

        Map<String, Object> tooMany = new LinkedHashMap<>();
        for (int index = 0; index < 64; index++) {
            tooMany.put("key-" + index, index);
        }
        exception.withContext(tooMany);
        assertThat(exception.getContext()).hasSize(32);
    }

    @Test
    void boundsCollectionValuesAndReturnsAnImmutableSnapshot() {
        CompileFlowException exception = new CompileFlowException(ErrorCode.CF_EXEC_008, "invalid decision")
            .withContext("values", java.util.stream.IntStream.range(0, 64).boxed().toList());
        Map<String, Object> snapshot = exception.getContext();
        exception.withContext("later", true);

        List<?> values = (List<?>) snapshot.get("values");
        assertThat(values).hasSize(32);
        assertThat(values.get(31)).isEqualTo("<truncated>");
        assertThat(snapshot).doesNotContainKey("later");
        assertThatThrownBy(() -> snapshot.put("other", 3)).isInstanceOf(UnsupportedOperationException.class);
    }
}
