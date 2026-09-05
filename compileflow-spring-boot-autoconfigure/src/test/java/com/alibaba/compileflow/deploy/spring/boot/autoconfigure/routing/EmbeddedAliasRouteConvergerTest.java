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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.control.repository.ProcessAliasRecord;
import com.alibaba.compileflow.deploy.control.repository.ProcessAliasRepository;
import com.alibaba.compileflow.deploy.runtime.LocalRoutingReconciler;
import com.alibaba.compileflow.deploy.runtime.install.ProcessArtifactRuntimeLoader;
import com.alibaba.compileflow.deploy.runtime.artifact.ProcessArtifactResolver;
import com.alibaba.compileflow.deploy.runtime.demand.VersionDemandPlanner;
import com.alibaba.compileflow.deploy.runtime.install.RuntimeInstaller;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;

class EmbeddedAliasRouteConvergerTest {
    private static final String NAMESPACE = "default";
    private static final String CODE = "order.embedded";
    private static final String ALIAS = "production";
    private static final String VERSION = "v1";

    private static ProcessAliasRecord aliasRecord() {
        return ProcessAliasRecord
            .builder()
            .namespace(NAMESPACE)
            .code(CODE)
            .alias(ALIAS)
            .stableVersion(VERSION)
            .revision(1L)
            .updatedBy("test")
            .updatedAt(1L)
            .build();
    }

    @Test
    void repositoryMissWaitsForTheSharedLocalReadyPipeline() throws Exception {
        ProcessAliasRepository repository = mock(ProcessAliasRepository.class);
        when(repository.resolve(NAMESPACE, CODE, ALIAS)).thenReturn(Optional.of(aliasRecord()));

        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessArtifactRuntimeLoader loader = mock(ProcessArtifactRuntimeLoader.class);
        CountDownLatch loadStarted = new CountDownLatch(1);
        CountDownLatch releaseLoad = new CountDownLatch(1);
        doAnswer(invocation -> {
            loadStarted.countDown();
            assertThat(releaseLoad.await(2, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(loader).load(any(ProcessArtifact.class));
        when(loader.release(NAMESPACE, CODE, VERSION)).thenReturn(ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED);
        ProcessArtifactResolver resolver =
                key -> new ProcessArtifact(ProcessRef.version(key.namespace(), key.code(), key.version()),
                        ProcessModelType.TBBPM, ProcessDefinition.inline(key.code(), "<definitions/>"),
                        DigestUtils.sha256Hex("<definitions/>"));

        ExecutorService installerExecutor = Executors.newSingleThreadExecutor();
        ExecutorService callerExecutor = Executors.newSingleThreadExecutor();
        RuntimeInstaller installer = RuntimeInstaller.withOwnedRetryScheduler(resolver, loader, installerExecutor,
                localRoutingState, Duration.ofMinutes(1), 1_000);
        LocalRoutingReconciler applier =
                new LocalRoutingReconciler(new VersionDemandPlanner(), installer, localRoutingState, java.util.Set.of());
        EmbeddedAliasRouteConverger handler =
                new EmbeddedAliasRouteConverger(repository, applier, Duration.ofSeconds(2));
        try {
            Future<?> miss = callerExecutor.submit(() -> handler.converge(ProcessRef.alias(NAMESPACE, CODE, ALIAS)));

            assertThat(loadStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(miss.isDone()).isFalse();
            assertThat(localRoutingState.getAliasRouteState().resolve(NAMESPACE, CODE, ALIAS)).isEmpty();

            releaseLoad.countDown();

            miss.get(2, TimeUnit.SECONDS);
            assertThat(localRoutingState.getAliasRouteState().resolve(NAMESPACE, CODE, ALIAS))
                .hasValueSatisfying(route -> assertThat(route.stableVersion().version()).isEqualTo(VERSION));
            assertThat(localRoutingState.getInstalledVersionState().contains(NAMESPACE, CODE, VERSION)).isTrue();
        } finally {
            releaseLoad.countDown();
            installer.close();
            verify(loader).release(NAMESPACE, CODE, VERSION);
            installerExecutor.shutdownNow();
            callerExecutor.shutdownNow();
        }
    }
}
