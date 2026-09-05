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

import static com.alibaba.compileflow.engine.core.runtime.RuntimeTestFixtures.bindingKey;
import static com.alibaba.compileflow.engine.core.runtime.RuntimeTestFixtures.runtimeIdentity;
import static com.alibaba.compileflow.engine.core.runtime.RuntimeTestFixtures.versioned;
import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ProcessRef;
import org.junit.jupiter.api.Test;

class ProcessRuntimeIdentityIsolationSemanticsTest {
    @Test
    void compilationIdentityIsSharedAcrossNamespacesWhenInputsAreIdentical() {
        ProcessRuntimeRequest alpha = versioned("team-alpha", "order.checkout", "1", "<process/>");
        ProcessRuntimeRequest beta = versioned("team-beta", "order.checkout", "1", "<process/>");

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        ProcessRuntimeIdentity alphaIdentity = runtimeIdentity(alpha, cl);
        ProcessRuntimeIdentity betaIdentity = runtimeIdentity(beta, cl);

        assertThat(betaIdentity)
            .as("Namespace isolates cache installation, not equivalent compilation inputs.")
            .isEqualTo(alphaIdentity);
    }

    @Test
    void compilationIdentityDiffersWhenContentChanges() {
        ProcessRuntimeRequest v1 = versioned("default", "order.checkout", "1", "<process version='1'/>");
        ProcessRuntimeRequest v2 = versioned("default", "order.checkout", "1", "<process version='2'/>");

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        assertThat(runtimeIdentity(v2, cl))
            .as("Processes with different content require independent compilations.")
            .isNotEqualTo(runtimeIdentity(v1, cl));
    }

    @Test
    void compilationIdentityIgnoresVersionWhenExactInputsMatch() {
        ProcessRuntimeRequest versionOne = versioned("default", "order.checkout", "1", "<process/>");
        ProcessRuntimeRequest versionTwo = versioned("default", "order.checkout", "2", "<process/>");

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        assertThat(runtimeIdentity(versionTwo, classLoader))
            .as("A publication version is not a compilation input.")
            .isEqualTo(runtimeIdentity(versionOne, classLoader));
    }

    @Test
    void cacheKeyDiffersAcrossNamespacesForSameCode() {
        String keyAlpha = com.alibaba.compileflow.engine.core.runtime.cache.RuntimeCacheKeys.forProcess("team-alpha",
                "order.checkout", "1.0");
        String keyBeta = com.alibaba.compileflow.engine.core.runtime.cache.RuntimeCacheKeys.forProcess("team-beta",
                "order.checkout", "1.0");

        assertThat(keyBeta)
            .as("Runtime cache keys must differ across namespaces to enforce per-namespace cache isolation.")
            .isNotEqualTo(keyAlpha);
    }

    @Test
    void bindingKeyIsNamespaceAware() {
        ProcessRuntimeRequest alpha =
                ProcessRuntimeRequest.from(ProcessRef.version("team-alpha", "order.checkout", "1.0"));
        ProcessRuntimeRequest beta =
                ProcessRuntimeRequest.from(ProcessRef.version("team-beta", "order.checkout", "1.0"));

        assertThat(bindingKey(beta))
            .as("Published runtime binding keys must preserve namespace isolation.")
            .isNotEqualTo(bindingKey(alpha));
    }
}
