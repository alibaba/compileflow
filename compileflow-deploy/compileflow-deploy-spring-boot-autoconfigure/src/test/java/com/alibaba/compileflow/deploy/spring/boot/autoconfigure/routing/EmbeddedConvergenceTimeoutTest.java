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
package com.alibaba.compileflow.deploy.spring.boot.autoconfigure.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.api.routing.ProcessAliasState;
import com.alibaba.compileflow.deploy.runtime.artifact.ProcessArtifactResolver;
import com.alibaba.compileflow.deploy.runtime.observability.DeploymentRuntimeMetrics;
import com.alibaba.compileflow.deploy.runtime.routing.LocalRoutingReconciler;
import com.alibaba.compileflow.deploy.runtime.routing.LocalRoutingConvergence;
import com.alibaba.compileflow.deploy.protocol.RoutingStateCodec;
import com.alibaba.compileflow.deploy.protocol.RoutingStateKeys;
import com.alibaba.compileflow.deploy.runtime.version.ProcessArtifactRuntimeLoader;
import com.alibaba.compileflow.deploy.runtime.version.VersionRuntimeManager;
import com.alibaba.compileflow.deploy.spi.store.ProcessAliasRecord;
import com.alibaba.compileflow.deploy.spi.store.ProcessAliasStore;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.CompileFlowEmbeddedDeploymentRuntimeAutoConfiguration;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.CompileFlowDeploymentProperties;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.DeploymentRuntimeProperties;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EmbeddedConvergenceTimeoutTest {
    private enum BlockingStage {
        ALIAS,
        SOURCE,
        LOAD
    }

    private enum EntryPoint {
        ALIAS,
        PROPAGATION,
        DELIVERY
    }

    private static Stream<Arguments> blockingStages() {
        return Stream
            .of(EntryPoint.values())
            .flatMap(entry -> Stream
                .of(BlockingStage.values())
                .filter(stage -> stage != BlockingStage.ALIAS || entry == EntryPoint.ALIAS)
                .map(stage -> Arguments.of(entry, stage)));
    }

    @ParameterizedTest
    @MethodSource("blockingStages")
    void firstConvergenceTimesOutWithoutCancellingBackgroundWork(EntryPoint entry, BlockingStage stage) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ProcessRef.Alias alias = ProcessRef.alias("default", "timeout.flow", "production");
        ProcessAliasRecord record = ProcessAliasRecord
            .builder()
            .namespace(alias.namespace())
            .code(alias.code())
            .alias(alias.alias())
            .stableVersion("v1")
            .revision(1L)
            .updatedBy("test")
            .updatedAt(1L)
            .build();
        ProcessAliasStore repository = mock(ProcessAliasStore.class);
        when(repository.resolve(alias.namespace(), alias.code(), alias.alias())).thenAnswer(invocation -> {
            block(stage, BlockingStage.ALIAS, started, release);
            return Optional.of(record);
        });
        ProcessArtifactResolver resolver =
                key -> {
            block(stage, BlockingStage.SOURCE, started, release);
            ProcessDefinition.Inline definition =
                    ProcessDefinition.inline(ProcessModelType.TBBPM, key.code(), "<flow/>");
            return new ProcessArtifact(key, definition, ProcessArtifactDigest.compute(definition, Map.of()));
        };
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        doAnswer(invocation -> {
            block(stage, BlockingStage.LOAD, started, release);
            return null;
        }).when(loader).load(any(ProcessArtifact.class));
        when(loader.release(any(), any(), any())).thenReturn(ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED);
        CompileFlowDeploymentProperties properties = mock(CompileFlowDeploymentProperties.class);
        when(properties.getRuntime())
            .thenReturn(new DeploymentRuntimeProperties(Duration.ofMinutes(1), Duration.ofMillis(100), 1));
        LocalRoutingState local = new LocalRoutingState();
        CompileFlowEmbeddedDeploymentRuntimeAutoConfiguration configuration =
                new CompileFlowEmbeddedDeploymentRuntimeAutoConfiguration();
        VersionRuntimeManager manager = configuration.embeddedVersionRuntimeManager(resolver, loader, local,
                ProcessEngineConfig.defaults(), properties, new DeploymentRuntimeMetrics());
        LocalRoutingReconciler reconciler = new LocalRoutingReconciler(manager, local, Set.of());
        ExecutorService callers = Executors.newSingleThreadExecutor();
        try (LocalRoutingConvergence convergence = configuration.embeddedLocalRoutingConvergence(reconciler, properties)) {
            String payload = RoutingStateCodec.aliasStateJson(alias.namespace(), alias.code(), alias.alias(), "v1", null,
                    null, 1L, "test", 1L);
            ProcessAliasState update = new ProcessAliasState(alias,
                    ProcessRef.version(alias.namespace(), alias.code(), "v1"), null, null, 1L, "test",
                    Instant.ofEpochMilli(1L));
            ThrowingCallable converge = switch (entry) {
                case ALIAS -> () -> configuration
                    .embeddedAliasRouteConverger(repository, convergence, properties)
                    .converge(alias);
                case PROPAGATION -> () -> configuration
                    .embeddedRoutingStatePropagator(convergence, properties)
                    .propagate(update);
                case DELIVERY -> () -> configuration
                    .embeddedRoutingStateDeliveryTarget(convergence, properties)
                    .deliver(RoutingStateKeys.aliasState("test.", alias.namespace(), alias.code(), alias.alias()),
                            payload);
            };
            Future<Throwable> result = callers.submit(() -> catchThrowable(converge));
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(result.get(1, TimeUnit.SECONDS)).isInstanceOfSatisfying(DeploymentException.class, failure -> {
                assertThat(failure.getErrorCode()).isEqualTo(DeploymentErrorCode.CONVERGENCE_FAILED);
                assertThat(failure.getAlias()).isEqualTo(alias.alias());
                assertThat(failure).hasMessageContaining("timed out");
            });
            assertThat(local.getAliasRouteState().resolve(alias.namespace(), alias.code(), alias.alias())).isEmpty();
            release.countDown();
            Awaitility
                .await()
                .atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(local
                    .getAliasRouteState()
                    .resolve(alias.namespace(), alias.code(), alias.alias()))
                    .isPresent());
            verify(loader).load(any(ProcessArtifact.class));
        } finally {
            release.countDown();
            callers.shutdown();
            assertThat(callers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            manager.close();
        }
    }

    private static void block(BlockingStage actual, BlockingStage expected, CountDownLatch started,
            CountDownLatch release) {
        if (actual != expected) {
            return;
        }
        started.countDown();
        try {
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Background convergence was interrupted", failure);
        }
    }
}
