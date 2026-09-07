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
package com.alibaba.compileflow.deploy.runtime.version;

import com.alibaba.compileflow.engine.ProcessModelType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.runtime.artifact.ProcessArtifactResolver;
import com.alibaba.compileflow.deploy.runtime.routing.AliasVersionDemand;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VersionRuntimeManagerCapacityTest {
    private static final String NAMESPACE = "default";
    private static final String CODE = "capacity.process";
    @Mock
    private ProcessArtifactResolver resolver;
    @Mock
    private ProcessArtifactRuntimeLoader loader;
    private LocalRoutingState localRoutingState;
    private ExecutorService executor;
    private VersionRuntimeManager manager;

    private static ProcessRef.Version key(String version) {
        return ProcessRef.version(NAMESPACE, CODE, version);
    }

    private static ProcessArtifact artifact(ProcessRef.Version key) {
        ProcessDefinition.Inline definition = ProcessDefinition.inline(ProcessModelType.TBBPM, key.code(), "<flow/>");
        ProcessRef.Version ref = ProcessRef.version(key.namespace(), key.code(), key.version());
        return new ProcessArtifact(ref, definition, ProcessArtifactDigest.compute(definition, Map.of()));
    }

    @BeforeEach
    void setUp() {
        localRoutingState = new LocalRoutingState();
        executor = Executors.newFixedThreadPool(3);
        manager = VersionRuntimeManager.withOwnedRetryScheduler(resolver, loader, executor, localRoutingState,
                Duration.ofMinutes(1), 2);
        when(loader.release(anyString(), anyString(), anyString()))
            .thenReturn(ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED);
    }

    @AfterEach
    void tearDown() {
        manager.close();
        executor.shutdownNow();
    }

    @Test
    void boundsInflightWorkAndDrainsPendingAliases() throws Exception {
        CountDownLatch firstTwoStarted = new CountDownLatch(2);
        CountDownLatch allowResolution = new CountDownLatch(1);
        when(resolver.resolve(any(ProcessRef.Version.class))).thenAnswer(invocation -> {
            firstTwoStarted.countDown();
            assertThat(allowResolution.await(2, TimeUnit.SECONDS)).isTrue();
            return artifact(invocation.getArgument(0));
        });

        CompletableFuture<Void> first = install("production", "v1");
        CompletableFuture<Void> second = install("preview", "v2");
        CompletableFuture<Void> third = install("testing", "v3");

        try {
            assertThat(firstTwoStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(manager.snapshot().getInflightCount()).isEqualTo(2);
            verify(resolver, times(2)).resolve(any(ProcessRef.Version.class));
        } finally {
            allowResolution.countDown();
        }

        CompletableFuture.allOf(first, second, third).get(2, TimeUnit.SECONDS);

        verify(resolver, times(3)).resolve(any(ProcessRef.Version.class));
        verify(loader, times(3)).load(any(ProcessArtifact.class));
        assertThat(manager.snapshot().getInflightCount()).isZero();
    }

    @Test
    void revalidatesAnExternallyDeployedRuntimeBeforeTakingOwnership() throws Exception {
        ProcessRef.Version key = key("v1");
        localRoutingState.getInstalledVersionState().markInstalled(key.namespace(), key.code(), key.version());
        when(resolver.resolve(key)).thenReturn(artifact(key));

        install("production", "v1").get(1, TimeUnit.SECONDS);

        org.mockito.InOrder calls = inOrder(resolver, loader);
        calls.verify(resolver).resolve(key);
        calls.verify(loader).load(any(ProcessArtifact.class));
        calls.verify(loader).prepare(key);
        assertThat(manager.snapshot().getRetainedRuntimeCount()).isEqualTo(1);
    }

    private CompletableFuture<Void> install(String alias, String version) {
        ProcessRef.Version key = key(version);
        return manager.ensureInstalled(AliasVersionDemand.forAlias(NAMESPACE, CODE, alias, Collections.singleton(key)));
    }
}
