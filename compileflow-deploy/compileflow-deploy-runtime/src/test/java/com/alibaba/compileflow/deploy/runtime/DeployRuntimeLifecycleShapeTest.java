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
package com.alibaba.compileflow.deploy.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import com.alibaba.compileflow.engine.core.routing.LocalRoutingState;
import com.alibaba.compileflow.deploy.runtime.demand.VersionDemandPlanner;
import com.alibaba.compileflow.deploy.runtime.install.RuntimeInstaller;
import com.alibaba.compileflow.deploy.runtime.state.RoutingStateSubscriber;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class DeployRuntimeLifecycleShapeTest {
    @Test
    void doesNotExposePublicBootstrap() {
        assertThat(Arrays
            .stream(DeployRuntime.class.getDeclaredMethods())
            .filter(method -> "bootstrap".equals(method.getName()))
            .filter(method -> Modifier.isPublic(method.getModifiers()))
            .map(Method::getName))
            .isEmpty();
        assertThat(Arrays.stream(DeployRuntime.class.getMethods()).map(Method::getName))
            .contains("start", "stop", "snapshot", "close");
    }

    @Test
    void closeCannotOvertakeAConcurrentStart() throws Exception {
        RoutingStateSubscriber subscriber = mock(RoutingStateSubscriber.class);
        RuntimeInstaller installer = mock(RuntimeInstaller.class);
        CountDownLatch startEntered = new CountDownLatch(1);
        CountDownLatch finishStart = new CountDownLatch(1);
        doAnswer(invocation -> {
            startEntered.countDown();
            assertThat(finishStart.await(2, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(subscriber).start(any());
        DeployRuntime runtime = new DeployRuntime(subscriber, new VersionDemandPlanner(), installer,
                new LocalRoutingState(), java.util.Set.of());

        CompletableFuture<Void> start = CompletableFuture.runAsync(runtime::start);
        assertThat(startEntered.await(2, TimeUnit.SECONDS)).isTrue();
        CountDownLatch closeSubmitted = new CountDownLatch(1);
        CompletableFuture<Void> close = CompletableFuture.runAsync(() -> {
            closeSubmitted.countDown();
            runtime.close();
        });
        assertThat(closeSubmitted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(close).isNotDone();

        finishStart.countDown();

        start.get(2, TimeUnit.SECONDS);
        close.get(2, TimeUnit.SECONDS);
        InOrder lifecycle = inOrder(subscriber, installer);
        lifecycle.verify(subscriber).start(any());
        lifecycle.verify(subscriber).close();
        lifecycle.verify(installer).close();
        assertThatIllegalStateException().isThrownBy(runtime::start);
    }
}
