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
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.ProcessError;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ProcessFailureClassifierTest {
    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void preservesExplicitCompileFlowFailures() {
        CompileFlowException failure = new CompileFlowException(ErrorCode.CF_EXEC_008, "Safe business message");

        assertThat(ProcessFailureClassifier.classify(failure)).isSameAs(failure);
    }

    @Test
    void doesNotExposeArbitraryApplicationFailureMessages() {
        IllegalStateException source = new IllegalStateException("credential=must-not-escape");

        CompileFlowException classified = ProcessFailureClassifier.classify(source);

        assertThat(classified.getErrorCode()).isEqualTo(ErrorCode.CF_EXEC_001);
        assertThat(classified.getMessage()).isEqualTo(ErrorCode.CF_EXEC_001.getMessage()).doesNotContain(
                "must-not-escape");
        assertThat(classified.getCause()).isSameAs(source);
    }

    @Test
    void classifiesKnownInfrastructureFailuresWithoutEchoingTheirMessages() {
        CompileFlowException timeout = ProcessFailureClassifier.classify(new TimeoutException("secret-timeout-detail"));
        CompileFlowException rejected =
                ProcessFailureClassifier.classify(new RejectedExecutionException("secret-capacity-detail"));

        assertThat(timeout.getErrorCode()).isEqualTo(ErrorCode.CF_EXEC_004);
        assertThat(timeout.getMessage()).isEqualTo(ErrorCode.CF_EXEC_004.getMessage());
        assertThat(rejected.getErrorCode()).isEqualTo(ErrorCode.CF_EXEC_005);
        assertThat(rejected.getMessage()).isEqualTo(ErrorCode.CF_EXEC_005.getMessage());
    }

    @Test
    void restoresTheInterruptFlagWhenClassifyingInterruption() {
        CompileFlowException classified =
                ProcessFailureClassifier.classify(new InterruptedException("secret-interrupt-detail"));

        assertThat(classified.getErrorCode()).isEqualTo(ErrorCode.CF_EXEC_007);
        assertThat(classified.getMessage()).isEqualTo(ErrorCode.CF_EXEC_007.getMessage());
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void convertsInternalDiagnosticsToAStableCallerSafeError() {
        CompileFlowException failure = new CompileFlowException(ErrorCode.CF_COMPILE_002,
                "Java compilation failed at /secret/path: credential=must-not-escape",
                new IllegalStateException("private compiler detail"));

        ProcessError error = ProcessFailureClassifier.toProcessError(failure);

        assertThat(error.getCode()).isEqualTo(ErrorCode.CF_COMPILE_002.getCode());
        assertThat(error.getMessage())
            .isEqualTo(ErrorCode.CF_COMPILE_002.getMessage())
            .doesNotContain("/secret/path", "must-not-escape");
        assertThat(failure.getMessage()).contains("must-not-escape");
    }

    @Test
    void exposesOnlyTheControlledProcessCallDepthDiagnostic() {
        ProcessCallDepthException failure = new ProcessCallDepthException(List.of("order", "payment", "order"), 2);

        ProcessError error = ProcessFailureClassifier.toProcessError(failure);

        assertThat(error.getCode()).isEqualTo("CF_EXEC_013");
        assertThat(error.getMessage())
            .isEqualTo("Process call depth 3 exceeds configured maximum 2: " + "order -> payment -> order");
    }
}
