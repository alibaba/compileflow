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
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ProcessResultTest {
    private static final ProcessExecution EXECUTION = executionBuilder().invocationId("inv-1").build();

    private static ProcessExecution.Builder executionBuilder() {
        return ProcessExecution
            .builder()
            .traceId("trace-1")
            .namespace("default")
            .processCode("order.flow")
            .startedAt(Instant.parse("2026-01-01T00:00:00Z"))
            .completedAt(Instant.parse("2026-01-01T00:00:01Z"));
    }

    @Test
    void mapsSuccessAndPreservesControlledExecution() {
        ProcessResult<String> result = ProcessResult.success("value", EXECUTION);

        ProcessResult<Integer> mapped = result.map(String::length);

        assertThat(mapped.isSuccess()).isTrue();
        assertThat(mapped.getOutput()).isEqualTo(5);
        assertThat(mapped.getError()).isNull();
        assertThat(mapped.getExecution()).isSameAs(EXECUTION);
    }

    @Test
    void nullOutputRemainsASuccessfulResult() {
        AtomicBoolean fallbackInvoked = new AtomicBoolean();
        ProcessResult<String> result = ProcessResult.success(null, EXECUTION);

        ProcessResult<Boolean> mapped = result.map(value -> value == null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.orElse("fallback")).isNull();
        assertThat(result.orElseGet(() -> {
            fallbackInvoked.set(true);
            return "fallback";
        })).isNull();
        assertThat(fallbackInvoked).isFalse();
        assertThat(mapped.orElseThrow()).isTrue();
    }

    @Test
    void mappingCallbackFailuresPropagate() {
        ProcessResult<String> result = ProcessResult.success("value", EXECUTION);
        IllegalStateException failure = new IllegalStateException("mapper failed");

        assertThatThrownBy(() -> result.map(value -> {
            throw failure;
        })).isSameAs(failure);
        assertThatThrownBy(() -> result.map(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void validatesTypedError() {
        assertThatThrownBy(() -> new ProcessError(null, "failed")).isInstanceOf(NullPointerException.class).hasMessage(
                "code");
        assertThatThrownBy(() -> new ProcessError("invalid code", "failed"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("code must start with A-Z");
        assertThatThrownBy(() -> new ProcessError("TEST_FAILURE", " "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("message must not be blank");
        assertThatThrownBy(() -> new ProcessError("TEST_FAILURE", "\u00a0"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("message must not be blank");
        assertThatThrownBy(() -> new ProcessError("TEST_FAILURE", "x".repeat(4_097)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("message must not exceed 4096 characters");
        assertThatThrownBy(() -> new ProcessError(" TEST_FAILURE ", "failed"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("surrounding whitespace");
        assertThat(new ProcessError("TEST_FAILURE", "  failed  ").getMessage()).isEqualTo("  failed  ");
    }

    @Test
    void processErrorBoundsUnicodeCharactersAndRejectsUnencodableText() {
        assertThat(new ProcessError("TEST_FAILURE", "\ud83d\ude00".repeat(4_096)).getMessage()).hasSize(8_192);
        assertThatThrownBy(() -> new ProcessError("TEST_FAILURE", "\ud83d\ude00".repeat(4_097)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("4096 characters");
        assertThatThrownBy(() -> new ProcessError("TEST_FAILURE", "failure\ud800"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("valid Unicode");
    }

    @Test
    void requiresAnInvocationIdentifierForExecutionAttribution() {
        assertThatThrownBy(() -> executionBuilder().build())
            .isInstanceOf(NullPointerException.class)
            .hasMessage("invocationId");
    }

    @Test
    void rejectsUnsafeInvocationIdentifiersForExecutionAttribution() {
        assertThatThrownBy(() -> executionBuilder().invocationId("request\nforged").build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invocationId");
    }

    @Test
    void exposesControlledRuntimeIdentity() {
        assertThat(EXECUTION.getTraceId()).isEqualTo("trace-1");
        assertThat(EXECUTION.getProcessCode()).isEqualTo("order.flow");
    }

    @Test
    void preservesTypedFailureAcrossMappingAndThrowing() {
        ProcessError error = new ProcessError("TEST_FAILURE", "failed");
        ProcessResult<String> result = ProcessResult.failure(error, EXECUTION);

        ProcessResult<Integer> mapped = result.map(String::length);

        assertThat(mapped.isFailure()).isTrue();
        assertThat(mapped.getError()).isSameAs(error);
        assertThat(mapped.getExecution()).isSameAs(EXECUTION);
        assertThat(mapped.orElse(7)).isEqualTo(7);
        assertThat(mapped.orElseGet(() -> 8)).isEqualTo(8);
        assertThatThrownBy(mapped::orElseThrow).isInstanceOfSatisfying(ProcessExecutionException.class, exception -> {
            assertThat(exception.getError()).isSameAs(error);
            assertThat(exception.getExecution()).isSameAs(EXECUTION);
        });
    }

    @Test
    void fallbackSuppliersAreLazy() {
        AtomicBoolean invoked = new AtomicBoolean();
        ProcessResult<String> success = ProcessResult.success("value", EXECUTION);

        assertThat(success.orElseGet(() -> {
            invoked.set(true);
            return "fallback";
        })).isEqualTo("value");
        assertThat(invoked).isFalse();
    }

    @Test
    void customExceptionSuppliersAreLazyAndAdaptFailures() {
        AtomicBoolean invoked = new AtomicBoolean();
        ProcessResult<String> success = ProcessResult.success("value", EXECUTION);

        assertThat(success.orElseThrow(() -> {
            invoked.set(true);
            return new IllegalStateException("unused");
        })).isEqualTo("value");
        assertThat(invoked).isFalse();

        ProcessResult<String> failure = ProcessResult.failure(new ProcessError("TEST_FAILURE", "failed"), EXECUTION);
        assertThatThrownBy(() -> failure.orElseThrow(() -> new IllegalStateException("translated")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("translated");
    }

    @Test
    void resultExceptionSerializesWithoutRuntimeAttribution() throws Exception {
        ProcessExecutionException exception =
                new ProcessExecutionException(new ProcessError("TEST_FAILURE", "failed"), EXECUTION);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(exception);
        }
        ProcessExecutionException restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (ProcessExecutionException) input.readObject();
        }

        assertThat(restored.getError()).isEqualTo(new ProcessError("TEST_FAILURE", "failed"));
        assertThat(restored.getExecution()).isNull();
    }

    @Test
    void stringSummaryDoesNotExposePayloadOrErrorMessage() {
        ProcessResult<String> success = ProcessResult.success("secret-result", EXECUTION);
        ProcessResult<String> failure =
                ProcessResult.failure(new ProcessError("TEST_FAILURE", "secret-error"), EXECUTION);

        assertThat(success.toString()).contains("success=true", "outputPresent=true").doesNotContain("secret-result");
        assertThat(failure.toString()).contains("success=false", "errorCode=TEST_FAILURE").doesNotContain(
                "secret-error");
    }
}
