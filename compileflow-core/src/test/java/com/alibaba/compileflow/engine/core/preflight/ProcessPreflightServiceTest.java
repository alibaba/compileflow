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
package com.alibaba.compileflow.engine.core.preflight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeEntry;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeRequest;
import com.alibaba.compileflow.engine.core.source.ProcessDefinitionSnapshot;
import com.alibaba.compileflow.engine.core.runtime.loading.ProcessRuntimeLoader;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightOptions;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightReport;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProcessPreflightServiceTest {
    private ExecutorService coordinator;

    @BeforeEach
    void setUp() {
        coordinator = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void tearDown() {
        coordinator.shutdownNow();
    }

    @Test
    void fatalErrorsFromAStageAreNotConvertedIntoReports() {
        AssertionError fatal = new AssertionError("fatal linter failure");
        ProcessPreflightService service =
                new ProcessPreflightService(new NoOpProcessRuntimeLoader(), source -> {
            throw fatal;
        }, coordinator);

        assertThatThrownBy(() -> service.preflight(getClass().getClassLoader(),
                ProcessDefinition.inline("order.checkout", "<flow/>"), ProcessPreflightOptions.fast()))
            .isSameAs(fatal);
    }

    @Test
    void unexpectedFailureMessageDoesNotEnterTheReport() {
        ProcessPreflightService service =
                new ProcessPreflightService(new NoOpProcessRuntimeLoader(), source -> {
            throw new IllegalStateException("credential=do-not-expose");
        }, coordinator);

        ProcessPreflightReport report = service.preflight(getClass().getClassLoader(),
                ProcessDefinition.inline("order.checkout", "<flow/>"), ProcessPreflightOptions.fast());

        assertThat(report.getCode()).isEqualTo("order.checkout");
        assertThat(report.getItems())
            .singleElement()
            .extracting(ProcessPreflightReport.Item::getMessage)
            .isEqualTo("preflight failed unexpectedly");
    }

    @Test
    void structuredCompileFlowDiagnosticIsPreserved() {
        ProcessPreflightService service =
                new ProcessPreflightService(new NoOpProcessRuntimeLoader(), source -> {
            throw new CompileFlowException(ErrorCode.CF_VALIDATION_005, "Start node is missing");
        }, coordinator);

        ProcessPreflightReport report = service.preflight(getClass().getClassLoader(),
                ProcessDefinition.inline("order.checkout", "<flow/>"), ProcessPreflightOptions.fast());

        assertThat(report.getItems())
            .singleElement()
            .extracting(ProcessPreflightReport.Item::getMessage)
            .isEqualTo("Start node is missing");
    }

    @Test
    void timeoutInterruptsTheActiveStageAndDoesNotContinueToCompilation() throws Exception {
        NoOpProcessRuntimeLoader runtimeLoader = new NoOpProcessRuntimeLoader();
        CountDownLatch lintStarted = new CountDownLatch(1);
        CountDownLatch lintInterrupted = new CountDownLatch(1);
        ProcessPreflightService service = new ProcessPreflightService(runtimeLoader, source -> {
            lintStarted.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException interrupted) {
                lintInterrupted.countDown();
                throw new IllegalStateException("lint interrupted", interrupted);
            }
        }, coordinator);
        ProcessPreflightOptions options = ProcessPreflightOptions
            .builder()
            .lintEnabled(true)
            .compileEnabled(true)
            .timeout(Duration.ofMillis(100))
            .build();

        ProcessPreflightReport report = service.preflight(getClass().getClassLoader(),
                ProcessDefinition.inline("order.checkout", "<flow/>"), options);

        assertThat(lintStarted.getCount()).isZero();
        assertThat(report.getItems())
            .singleElement()
            .extracting(ProcessPreflightReport.Item::getStatus)
            .isEqualTo(ProcessPreflightReport.ItemStatus.TIMEOUT);
        assertThat(lintInterrupted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(runtimeLoader.compileCalled()).isFalse();
    }

    @Test
    void rejectedCoordinatorWorkUsesTheStableExecutorOverloadCode() {
        coordinator.shutdown();
        ProcessPreflightService service =
                new ProcessPreflightService(new NoOpProcessRuntimeLoader(), source -> {}, coordinator);

        assertThatThrownBy(() -> service.preflight(getClass().getClassLoader(),
                ProcessDefinition.inline("order.checkout", "<flow/>"), ProcessPreflightOptions.fast()))
            .isInstanceOfSatisfying(CompileFlowException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(ErrorCode.CF_EXEC_005));
    }

    private static class NoOpProcessRuntimeLoader implements ProcessRuntimeLoader {
        private final AtomicBoolean compileCalled = new AtomicBoolean();

        @Override
        public CompletableFuture<ProcessRuntimeEntry> loadAsync(ProcessRuntimeRequest request, ClassLoader classLoader,
                ProcessRuntimeEntry expectedEntry) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public ProcessRuntimeEntry loadSync(ProcessRuntimeRequest request, ClassLoader classLoader) {
            return null;
        }

        @Override
        public ProcessRuntimeEntry loadExactSync(ProcessRuntimeRequest request, ClassLoader classLoader) {
            return null;
        }

        @Override
        public ProcessDefinitionSnapshot resolve(ProcessRuntimeRequest request, ClassLoader classLoader) {
            return ProcessDefinitionSnapshot.of(request.getNamespace(), request.getCode(), request.getVersion(),
                    "<flow/>".getBytes(StandardCharsets.UTF_8), "test");
        }

        @Override
        public ProcessRuntimeEntry runtimeCheckSync(ProcessDefinitionSnapshot definition, ClassLoader classLoader) {
            compileCalled.set(true);
            return null;
        }

        boolean compileCalled() {
            return compileCalled.get();
        }

        @Override
        public void loadBatch(String ownerId, ClassLoader classLoader, ProcessRuntimeRequest... requests) {}

        @Override
        public void loadExactBatch(ClassLoader classLoader, ProcessRuntimeRequest... requests) {}

        @Override
        public void close() {}
    }
}
