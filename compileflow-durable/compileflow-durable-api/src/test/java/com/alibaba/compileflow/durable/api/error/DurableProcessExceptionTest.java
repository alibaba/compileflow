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
package com.alibaba.compileflow.durable.api.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

class DurableProcessExceptionTest {
    @Test
    void preservesBoundedContextAcrossSerialization() throws Exception {
        DurableProcessException original = DurableProcessException
            .builder(DurableErrorCode.PROCESS_IDENTITY_MISMATCH, "Stored Process identity conflicts")
            .context("processId", "0198f932-8c23-7818-82b4-8f997c6a1d80")
            .build();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }

        DurableProcessException restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (DurableProcessException) input.readObject();
        }

        assertThat(restored.getErrorCode()).isEqualTo(DurableErrorCode.PROCESS_IDENTITY_MISMATCH);
        assertThat(restored.getContext()).containsExactlyEntriesOf(original.getContext());
        assertThatThrownBy(() -> restored.getContext().put("another", "value"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void contextIsBoundedImmutableAndValuesAreRedactedFromString() {
        DurableProcessException failure = DurableProcessException
            .builder(DurableErrorCode.PROCESS_IDENTITY_MISMATCH, "Stored Process identity conflicts")
            .context("processId", "secret-like-value")
            .build();

        assertThat(failure.getErrorCode()).isEqualTo(DurableErrorCode.PROCESS_IDENTITY_MISMATCH);
        assertThat(failure.toString()).contains("processId").doesNotContain("secret-like-value");
        assertThatThrownBy(() -> failure.getContext().put("another", "value"))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
