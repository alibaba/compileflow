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
package com.alibaba.compileflow.engine.core.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InstalledVersionStateTest {
    private InstalledVersionState snapshot;

    @BeforeEach
    void setUp() {
        snapshot = new InstalledVersionState();
    }

    @Test
    void markUninstalledShouldRemoveExistingVersion() {
        snapshot.markInstalled("default", "order.flow", "v1");
        assertThat(snapshot.contains("default", "order.flow", "v1")).isTrue();

        boolean removed = snapshot.markUninstalled("default", "order.flow", "v1");

        assertThat(removed).isTrue();
        assertThat(snapshot.contains("default", "order.flow", "v1")).isFalse();
    }

    @Test
    void markUninstalledShouldReturnFalseWhenVersionNotPresent() {
        boolean removed = snapshot.markUninstalled("default", "order.flow", "v1");

        assertThat(removed).isFalse();
    }

    @Test
    void markUninstalledShouldCleanupEmptySet() {
        snapshot.markInstalled("default", "order.flow", "v1");

        snapshot.markUninstalled("default", "order.flow", "v1");

        assertThat(snapshot.hasAny("default", "order.flow")).isFalse();
        assertThat(snapshot.list("default", "order.flow")).isNotPresent();
    }

    @Test
    void markUninstalledShouldPreserveOtherVersions() {
        snapshot.markInstalled("default", "order.flow", "v1");
        snapshot.markInstalled("default", "order.flow", "v2");

        boolean removed = snapshot.markUninstalled("default", "order.flow", "v1");

        assertThat(removed).isTrue();
        assertThat(snapshot.contains("default", "order.flow", "v1")).isFalse();
        assertThat(snapshot.contains("default", "order.flow", "v2")).isTrue();
    }

    @Test
    void explicitNamespaceShouldRejectNull() {
        assertThatThrownBy(() -> snapshot.markInstalled(null, "order.flow", "v1"))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("namespace");
    }

    @Test
    void invalidIdentityShouldFailFast() {
        snapshot.markInstalled("default", "order.flow", "v1");

        assertThatThrownBy(() -> snapshot.markUninstalled("default", "", "v1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("code");
        assertThatThrownBy(() -> snapshot.markUninstalled("default", "order.flow", ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("version");
        assertThat(snapshot.contains("default", "order.flow", "v1")).isTrue();
    }

    @Test
    void removeAllShouldRemoveAllVersionsForFlow() {
        snapshot.markInstalled("default", "order.flow", "v1");
        snapshot.markInstalled("default", "order.flow", "v2");
        snapshot.markInstalled("default", "order.flow", "v3");

        Set<String> removed = snapshot.removeAll("default", "order.flow");

        assertThat(removed).containsExactlyInAnyOrder("v1", "v2", "v3");
        assertThat(snapshot.hasAny("default", "order.flow")).isFalse();
    }

    @Test
    void removeAllShouldReturnEmptySetWhenNoVersionsExist() {
        Set<String> removed = snapshot.removeAll("default", "order.flow");

        assertThat(removed).isEmpty();
    }

    @Test
    void removeAllShouldNotAffectOtherFlows() {
        snapshot.markInstalled("default", "order.flow", "v1");
        snapshot.markInstalled("default", "order.flow", "v2");
        snapshot.markInstalled("default", "payment.flow", "v1");

        Set<String> removed = snapshot.removeAll("default", "order.flow");

        assertThat(removed).containsExactlyInAnyOrder("v1", "v2");
        assertThat(snapshot.hasAny("default", "order.flow")).isFalse();
        assertThat(snapshot.contains("default", "payment.flow", "v1")).isTrue();
    }

    @Test
    void concurrentUnloadShouldBeSafe() throws Exception {
        for (int i = 0; i < 100; i++) {
            snapshot.markInstalled("default", "order.flow", "v" + i);
        }

        Thread[] threads = new Thread[10];
        for (int t = 0; t < 10; t++) {
            final int threadId = t;
            threads[t] = new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = threadId * 10; i < (threadId + 1) * 10; i++) {
                        snapshot.markUninstalled("default", "order.flow", "v" + i);
                    }
                }
            });
            threads[t].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertThat(snapshot.hasAny("default", "order.flow")).isFalse();
    }

    @Test
    void versionShouldRejectSurroundingWhitespace() {
        assertThatThrownBy(() -> snapshot.markInstalled("default", "order.flow", "  v1  "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("whitespace");
    }

    @Test
    void lifecycleTestDeployAndUnload() {
        String ns = "default";
        String code = "order.flow";

        snapshot.markInstalled(ns, code, "v1");
        assertThat(snapshot.contains(ns, code, "v1")).isTrue();

        snapshot.markInstalled(ns, code, "v2");
        assertThat(snapshot.contains(ns, code, "v2")).isTrue();
        Optional<Set<String>> versions = snapshot.list(ns, code);
        assertThat(versions).isPresent();
        assertThat(versions.get()).containsExactlyInAnyOrder("v1", "v2");

        boolean removed = snapshot.markUninstalled(ns, code, "v1");
        assertThat(removed).isTrue();
        assertThat(snapshot.contains(ns, code, "v1")).isFalse();
        assertThat(snapshot.contains(ns, code, "v2")).isTrue();

        snapshot.markInstalled(ns, code, "v3");

        snapshot.markUninstalled(ns, code, "v3");
        assertThat(snapshot.contains(ns, code, "v3")).isFalse();
        versions = snapshot.list(ns, code);
        assertThat(versions).isPresent();
        assertThat(versions.get()).containsExactly("v2");
    }

    @Test
    void listReturnsAnImmutablePointInTimeSnapshot() {
        snapshot.markInstalled("default", "order.flow", "v1");

        Set<String> observed = snapshot.list("default", "order.flow").orElseThrow();
        snapshot.markInstalled("default", "order.flow", "v2");

        assertThat(observed).containsExactly("v1");
        assertThat(snapshot.list("default", "order.flow").orElseThrow()).containsExactlyInAnyOrder("v1", "v2");
        assertThatThrownBy(() -> observed.add("v3")).isInstanceOf(UnsupportedOperationException.class);
    }
}
