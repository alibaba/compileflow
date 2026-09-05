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
package com.alibaba.compileflow.engine.test.feature.invocationpolicy;

import com.alibaba.compileflow.engine.ProcessDefinition;
import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.ProcessExecutionOptions;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.config.ProcessExecutorConfig;
import com.alibaba.compileflow.engine.spi.execution.ActionExecutionContext;
import com.alibaba.compileflow.engine.spi.execution.FailureResolution;
import com.alibaba.compileflow.engine.test.support.config.ProcessEngineTestConfiguration;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessEngineTestFactory;
import com.alibaba.compileflow.engine.test.support.mocks.InvocationPolicyTestService;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProcessEngineTestConfiguration.class)
@DisplayName("InvocationPolicy Integration Tests")
class InvocationPolicyIntegrationTest {
    protected ProcessEngine engine;

    @BeforeEach
    void setUp() {
        InvocationPolicyTestService.resetCounters();
        engine = ProcessEngineFactory.create(ProcessEngineTestFactory
            .tbbpmBuilder()
            .executors(ProcessExecutorConfig.builder().actionTimeoutMaxConcurrency(4).build())
            .failureHandler("custom-failure-handler", context -> FailureResolution.CONTINUE_PROCESS)
            .build());
    }

    @AfterEach
    void tearDown() {
        try {
            if (engine != null) {
                engine.close();
            }
        } finally {
            InvocationPolicyTestService.resetCounters();
        }
    }

    @Nested
    @DisplayName("Retry scenarios")
    class RetryTests {
        @Test
        @DisplayName("should retry on transient failures and eventually succeed")
        void retriesTransientFailuresAndEventuallySucceeds() {
            Map<String, Object> context = Maps.newHashMap();
            context.put("failUntilAttempt", 2);

            ProcessResult<Map<String, Object>> result = engine.execute(ProcessDefinition.classpath("bpm.invocation-"
                            + "policy.retryTransientFailures",
                            "bpm.invocation-policy.retryTransientFailures".replace(".", "/") + ".bpm"), context);

            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).as("Retry should eventually succeed").isTrue();
            assertThat(result.getOutput()).containsEntry("result", "success_after_retry");
            assertThat(InvocationPolicyTestService.actionContexts())
                .extracting(ActionExecutionContext::getAttemptNumber)
                .containsExactly(1, 2);
            assertThat(InvocationPolicyTestService.actionContexts())
                .extracting(ActionExecutionContext::getInvocationKey)
                .containsOnly(InvocationPolicyTestService.actionContexts().get(0).getInvocationKey());
            assertThat(InvocationPolicyTestService.actionContexts().get(0).getProcessInvocationId())
                .isEqualTo(result.getExecution().getInvocationId());
        }

        @Test
        @DisplayName("should derive the same action key when a process invocation is replayed")
        void derivesStableInvocationKeyAcrossProcessReplay() {
            ProcessExecutionOptions options =
                    ProcessExecutionOptions.builder().invocationId("invocation-policy-replay-1").build();
            Map<String, Object> context = Map.of("failUntilAttempt", 2);

            ProcessResult<Map<String, Object>> first = engine.execute(ProcessDefinition.classpath("bpm.invocation-"
                            + "policy.retryTransientFailures",
                            "bpm.invocation-policy.retryTransientFailures".replace(".", "/") + ".bpm"), context, options);
            String firstKey = InvocationPolicyTestService.actionContexts().get(0).getInvocationKey();

            InvocationPolicyTestService.resetCounters();
            ProcessResult<Map<String, Object>> replay = engine.execute(ProcessDefinition.classpath("bpm.invocation-"
                            + "policy.retryTransientFailures",
                            "bpm.invocation-policy.retryTransientFailures".replace(".", "/") + ".bpm"), context, options);
            String replayKey = InvocationPolicyTestService.actionContexts().get(0).getInvocationKey();

            assertThat(first.isSuccess()).isTrue();
            assertThat(replay.isSuccess()).isTrue();
            assertThat(firstKey).isEqualTo(replayKey);
            assertThat(first.getExecution().getInvocationId()).isEqualTo("invocation-policy-replay-1");
        }

        @Test
        @DisplayName("should exhaust retries and fail on persistent failures")
        void failsAfterExhaustingRetries() {
            Map<String, Object> context = Maps.newHashMap();
            context.put("failUntilAttempt", 10);

            ProcessResult<Map<String, Object>> result = engine.execute(ProcessDefinition.classpath("bpm.invocation-"
                            + "policy.retryTransientFailures",
                            "bpm.invocation-policy.retryTransientFailures".replace(".", "/") + ".bpm"), context);

            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).as("Should fail after retry exhaustion").isFalse();
            assertThat(result.getError().getMessage()).as("Error message should be present").isNotNull();
        }

        @Test
        @DisplayName("should respect initial backoff configuration")
        void appliesConfiguredInitialBackoff() {
            Map<String, Object> context = Maps.newHashMap();
            context.put("failUntilAttempt", 2);
            String source = engine
                .tooling()
                .generateJavaCode(ProcessDefinition.classpath("bpm.invocation-policy.retryWithInterval",
                        "bpm/invocation-policy/retryWithInterval.bpm"));

            long startTime = System.nanoTime();
            ProcessResult<Map<String, Object>> result = engine.execute(ProcessDefinition.classpath("bpm.invocation-"
                            + "policy.retryWithInterval",
                            "bpm.invocation-policy.retryWithInterval".replace(".", "/") + ".bpm"), context);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

            assertThat(source).contains("RetryJitter.NONE");
            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).as("Retry with interval should succeed").isTrue();
            assertThat(elapsedMillis).as("Should apply the configured 200ms initial backoff").isGreaterThanOrEqualTo(
                    180);
        }

        @Test
        @DisplayName("should not retry on deterministic failures")
        void doesNotRetryDeterministicFailures() {
            Map<String, Object> context = Maps.newHashMap();

            ProcessResult<Map<String, Object>> result = engine.execute(ProcessDefinition.classpath("bpm.invocation-"
                            + "policy.retryNeverPolicy",
                            "bpm.invocation-policy.retryNeverPolicy".replace(".", "/") + ".bpm"), context);

            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).as("Should fail without retry").isFalse();
            assertThat(result.getError().getMessage()).as("Error message should be present").isNotNull();
        }
    }

    @Nested
    @DisplayName("Failure handler integration")
    class FailureHandlerTests {
        @Test
        @DisplayName("should use FAIL_FAST failure handler")
        void failsImmediatelyWithFailFastHandler() {
            Map<String, Object> context = Maps.newHashMap();

            ProcessResult<Map<String, Object>> result = engine.execute(ProcessDefinition.classpath("bpm.invocation-"
                            + "policy.failFastHandler",
                            "bpm.invocation-policy.failFastHandler".replace(".", "/") + ".bpm"), context);

            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).as("Fail-fast should fail immediately").isFalse();
            assertThat(result.getError().getMessage()).as("Error message should be present").isNotNull();
        }

        @Test
        @DisplayName("should use custom failure handler")
        void continuesWithCustomFailureHandler() {
            Map<String, Object> context = Maps.newHashMap();

            ProcessResult<Map<String, Object>> result = engine.execute(ProcessDefinition.classpath("bpm.invocation-"
                            + "policy.customFailureHandler",
                            "bpm.invocation-policy.customFailureHandler".replace(".", "/") + ".bpm"), context);

            assertThat(result.isSuccess()).as("Custom handler should continue the process after transient failure").isTrue();
        }
    }

    @Nested
    @DisplayName("Action executor integration")
    class ActionExecutorIntegrationTests {
        @Test
        @DisplayName("should apply default action parameters inside an invocation policy")
        void appliesDefaultActionParameterInsideInvocationPolicy() {
            String source = engine
                .tooling()
                .generateJavaCode(ProcessDefinition.classpath("bpm.invocation-policy.fullConfiguration",
                        "bpm/invocation-policy/fullConfiguration.bpm"));
            ProcessResult<Map<String, Object>> result = engine.execute(ProcessDefinition.classpath("bpm.invocation-"
                            + "policy.fullConfiguration",
                            "bpm.invocation-policy.fullConfiguration".replace(".", "/") + ".bpm"), Map.of());

            assertThat(source).contains("RetryJitter.FULL");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("result", "default_7");
        }

        @Test
        @DisplayName("should handle InvocationPolicy in parallel gateway branches")
        void appliesInvocationPolicyInParallelBranches() {
            Map<String, Object> context = Maps.newHashMap();
            context.put("a", 10);
            context.put("b", 20);

            ProcessResult<Map<String, Object>> result = engine.execute(ProcessDefinition.classpath("bpm.invocation-"
                            + "policy.parallelWithInvocationPolicy",
                            "bpm.invocation-policy.parallelWithInvocationPolicy".replace(".", "/") + ".bpm"), context);

            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).as("Parallel execution with job policies should succeed").isTrue();
            assertThat(result.getOutput())
                .as("Both branch results should be present")
                .containsKeys("branch1Result", "branch2Result");
        }

        @Test
        @DisplayName("should not commit a mapped result from a timed-out attempt")
        void doesNotCommitMappedResultFromTimedOutAttempt() {
            String source = engine
                .tooling()
                .generateJavaCode(ProcessDefinition.classpath("bpm.invocation-policy.timeoutOutputIsolation",
                        "bpm/invocation-policy/timeoutOutputIsolation.bpm"));
            ProcessResult<Map<String, Object>> result = engine.execute(ProcessDefinition.classpath("bpm.invocation-"
                            + "policy.timeoutOutputIsolation",
                            "bpm.invocation-policy.timeoutOutputIsolation".replace(".", "/") + ".bpm"), Map.of());

            assertThat(source)
                .contains("ActionExecutor.callAndCommit(", "_cf$nodeId,",
                        "_cf$executeReturnLateValueAfterInterruption(\"task\")")
                .contains("_cf$actionResult -> this.result = _cf$actionResult")
                .contains("return new InvocationPolicyTestService()" + ".returnLateValueAfterInterruption();")
                .doesNotContain("result = new InvocationPolicyTestService()" + ".returnLateValueAfterInterruption();");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("result", "initial");
        }
    }
}
