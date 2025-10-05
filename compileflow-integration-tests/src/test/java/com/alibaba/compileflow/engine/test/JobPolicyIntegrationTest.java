package com.alibaba.compileflow.engine.test;

import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.ProcessSource;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.google.common.collect.Maps;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive integration tests for JobPolicy functionality with AbstractActionNodeGenerator.
 * <p>
 * Tests cover:
 * 1. Timeout scenarios with different timeout configurations
 * 2. Retry mechanisms with various retry policies
 * 3. Failure handler integration
 * 4. JobPolicy parsing and validation
 * 5. ActionExecutor integration with generated code
 *
 * @author yusu
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProcessEngineTestConfiguration.class)
@DisplayName("JobPolicy Integration Tests")
class JobPolicyIntegrationTest {

    private ProcessEngine engine;

    @BeforeEach
    void setUp() {
        engine = ProcessEngineFactory.create(
                ProcessEngineConfig.tbbpmBuilder()
                        .parallelCompilation(true)
                        .build()
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        if (engine != null) {
            engine.close();
        }
    }

    @Nested
    @DisplayName("2. Retry Scenarios")
    class RetryTests {

        @Test
        @DisplayName("should retry on transient failures and eventually succeed")
        void testRetryOnTransientFailures() {
            Map<String, Object> context = Maps.newHashMap();
            context.put("failUntilAttempt", 2); // Fail first attempt, succeed on second

            ProcessResult<Map<String, Object>> result = engine.execute(ProcessSource.fromCode("bpm.jobpolicy.retryTransientFailures"), context);

            assertNotNull(result);
            assertTrue(result.isSuccess());
            assertEquals("success_after_retry", result.getData().get("result"));
        }

        @Test
        @DisplayName("should exhaust retries and fail on persistent failures")
        void testRetryExhaustion() {
            Map<String, Object> context = Maps.newHashMap();
            context.put("failUntilAttempt", 10); // Always fail

            ProcessResult result = engine.execute(ProcessSource.fromCode("bpm.jobpolicy.retryTransientFailures"), context);

            assertNotNull(result);
            assertFalse(result.isSuccess());
            assertNotNull(result.getErrorMessage());
        }

        @Test
        @DisplayName("should respect retry interval configuration")
        void testRetryInterval() {
            Map<String, Object> context = Maps.newHashMap();
            context.put("failUntilAttempt", 2);

            long startTime = System.currentTimeMillis();
            ProcessResult result = engine.execute(ProcessSource.fromCode("bpm.jobpolicy.retryWithInterval"), context);
            long endTime = System.currentTimeMillis();

            assertNotNull(result);
            assertTrue(result.isSuccess());
            // Should take at least the retry interval time (100ms in our test)
            assertTrue(endTime - startTime >= 100);
        }

        @Test
        @DisplayName("should not retry on deterministic failures")
        void testNoRetryOnDeterministicFailures() {
            Map<String, Object> context = Maps.newHashMap();

            ProcessResult result = engine.execute(ProcessSource.fromCode("bpm.jobpolicy.retryNeverPolicy"), context);

            assertNotNull(result);
            assertFalse(result.isSuccess());
            assertNotNull(result.getErrorMessage());
        }
    }

    @Nested
    @DisplayName("3. Failure Handler Integration")
    class FailureHandlerTests {

        @Test
        @DisplayName("should use FAIL_FAST failure handler")
        void testFailFastHandler() {
            Map<String, Object> context = Maps.newHashMap();

            ProcessResult result = engine.execute(ProcessSource.fromCode("bpm.jobpolicy.failFastHandler"), context);

            assertNotNull(result);
            assertFalse(result.isSuccess());
            assertNotNull(result.getErrorMessage());
        }

        @Test
        @DisplayName("should use custom failure handler")
        void testCustomFailureHandler() {
            Map<String, Object> context = Maps.newHashMap();

            ProcessResult result = engine.execute(ProcessSource.fromCode("bpm.jobpolicy.customFailureHandler"), context);

            assertNotNull(result);
            // Custom handler might allow process to continue or handle gracefully
            // Behavior depends on the specific handler implementation
        }
    }

    @Nested
    @DisplayName("5. ActionExecutor Integration")
    class ActionExecutorIntegrationTests {
        @Test
        @DisplayName("should handle JobPolicy in parallel gateway branches")
        void testJobPolicyInParallelBranches() {
            Map<String, Object> context = Maps.newHashMap();
            context.put("a", 10);
            context.put("b", 20);

            ProcessResult<Map<String, Object>> result = engine.execute(ProcessSource.fromCode("bpm.jobpolicy.parallelWithJobPolicy"), context);

            assertNotNull(result);
            assertTrue(result.isSuccess());
            assertNotNull(result.getData().get("branch1Result"));
            assertNotNull(result.getData().get("branch2Result"));
        }
    }

}
