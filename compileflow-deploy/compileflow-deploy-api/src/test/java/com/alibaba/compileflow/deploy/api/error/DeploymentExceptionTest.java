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
package com.alibaba.compileflow.deploy.api.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static java.util.Map.entry;
import com.alibaba.compileflow.engine.ProcessRef;
import org.junit.jupiter.api.Test;

class DeploymentExceptionTest {
    @Test
    void builderNormalizesContextAndExposesImmutableStructuredFields() {
        IllegalStateException cause = new IllegalStateException("store unavailable");
        DeploymentException failure = DeploymentException
            .builder(DeploymentErrorCode.STORAGE_ERROR, "publication failed", cause)
            .namespace(" tenant-a ")
            .code(" order.checkout ")
            .version(" v7 ")
            .alias(" stable ")
            .build();

        assertThat(failure.getErrorCode()).isEqualTo(DeploymentErrorCode.STORAGE_ERROR);
        assertThat(failure.getMessage()).isEqualTo("publication failed");
        assertThat(failure.getCause()).isSameAs(cause);
        assertThat(failure.getNamespace()).isEqualTo("tenant-a");
        assertThat(failure.getCode()).isEqualTo("order.checkout");
        assertThat(failure.getVersion()).isEqualTo("v7");
        assertThat(failure.getAlias()).isEqualTo("stable");
        assertThat(failure.toLogFields())
            .containsExactly(entry("errorCode", "STORAGE_ERROR"), entry("namespace", "tenant-a"),
                    entry("code", "order.checkout"), entry("version", "v7"), entry("alias", "stable"));
        assertThatThrownBy(() -> failure.toLogFields().put("unexpected", "value"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void versionFactoriesPreserveExactIdentityAndOptionalCause() {
        ProcessRef.Version ref = ProcessRef.version("tenant-a", "order.checkout", "v7");
        RuntimeException cause = new RuntimeException("source unavailable");

        DeploymentException withoutCause =
                DeploymentException.fromRef(DeploymentErrorCode.VERSION_NOT_FOUND, "missing", ref);
        DeploymentException withCause =
                DeploymentException.fromRef(DeploymentErrorCode.VERSION_NOT_FOUND, "missing", ref, cause);

        assertThat(withoutCause.toLogFields())
            .containsExactly(entry("errorCode", "VERSION_NOT_FOUND"), entry("namespace", "tenant-a"),
                    entry("code", "order.checkout"), entry("version", "v7"));
        assertThat(withCause.getCause()).isSameAs(cause);
        assertThat(withCause.toLogFields()).isEqualTo(withoutCause.toLogFields());
    }

    @Test
    void nullVersionFactoriesProduceContextFreeFailures() {
        RuntimeException cause = new RuntimeException("lookup failed");

        DeploymentException withoutCause =
                DeploymentException.fromRef(DeploymentErrorCode.VERSION_NOT_FOUND, "missing", null);
        DeploymentException withCause =
                DeploymentException.fromRef(DeploymentErrorCode.VERSION_NOT_FOUND, "missing", null, cause);

        assertThat(withoutCause.toLogFields()).containsExactly(entry("errorCode", "VERSION_NOT_FOUND"));
        assertThat(withCause.getCause()).isSameAs(cause);
        assertThat(withCause.getNamespace()).isNull();
        assertThat(withCause.getCode()).isNull();
        assertThat(withCause.getVersion()).isNull();
        assertThat(withCause.getAlias()).isNull();
    }

    @Test
    void rejectsMissingErrorCodeAtEveryConstructionBoundary() {
        assertThatThrownBy(() -> DeploymentException.of(null, "invalid"))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("errorCode");
        assertThatThrownBy(() -> DeploymentException.builder(null, "invalid"))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("errorCode");
    }
}
