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
package com.alibaba.compileflow.deploy.runtime.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.core.runtime.ownership.ProcessRuntimeOwnership;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentMetrics;
import com.alibaba.compileflow.deploy.runtime.install.ProcessArtifactRuntimeLoader;
import org.junit.jupiter.api.Test;

class ProcessArtifactRuntimeLoaderMetricsTest {
    @Test
    void loaderCountsDigestMismatchAtTheRuntimeTrustBoundary() {
        ProcessDeploymentMetrics metrics = new ProcessDeploymentMetrics();
        ProcessArtifactRuntimeLoader loader = new ProcessArtifactRuntimeLoader(mock(ProcessRuntimeOwnership.class),
                ignored -> java.util.List.of(), ProcessModelType.BPMN, ignored -> null, metrics);
        ProcessArtifact artifact = new ProcessArtifact(ProcessRef.version("default", "metrics.flow", "v1"),
                ProcessModelType.BPMN, ProcessDefinition.inline("metrics.flow", "<definitions/>"), "0".repeat(64));

        assertThatThrownBy(() -> loader.load(artifact))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.ARTIFACT_DIGEST_MISMATCH));
        assertThat(metrics.getErrorCount(DeploymentErrorCode.ARTIFACT_DIGEST_MISMATCH)).isEqualTo(1L);
    }
}
