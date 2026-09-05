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
package com.alibaba.compileflow.deploy.api.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentMetrics.AliasConvergenceReason;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentMetrics.AttemptOutcome;
import com.alibaba.compileflow.deploy.api.observability.ProcessDeploymentMetrics.RuntimeInstallReason;
import org.junit.jupiter.api.Test;

final class ProcessDeploymentMetricsTest {
    @Test
    void recordsMonotonicBoundedCounters() {
        ProcessDeploymentMetrics metrics = new ProcessDeploymentMetrics();

        metrics.recordRuntimeInstall(AttemptOutcome.SUCCESS, RuntimeInstallReason.NONE);
        metrics.recordRuntimeInstall(AttemptOutcome.FAILURE, RuntimeInstallReason.ARTIFACT_INTEGRITY);
        metrics.recordAliasConvergence(AttemptOutcome.SUCCESS, AliasConvergenceReason.NONE);
        metrics.recordAliasConvergence(AttemptOutcome.FAILURE, AliasConvergenceReason.INSTALLATION);
        metrics.recordError(DeploymentErrorCode.ARTIFACT_DIGEST_MISMATCH);

        assertThat(metrics.runtimeInstallCount(AttemptOutcome.SUCCESS, RuntimeInstallReason.NONE)).isEqualTo(1L);
        assertThat(metrics.runtimeInstallCount(AttemptOutcome.FAILURE, RuntimeInstallReason.ARTIFACT_INTEGRITY)).isEqualTo(
                1L);
        assertThat(metrics.aliasConvergenceCount(AttemptOutcome.SUCCESS, AliasConvergenceReason.NONE)).isEqualTo(1L);
        assertThat(metrics.aliasConvergenceCount(AttemptOutcome.FAILURE, AliasConvergenceReason.INSTALLATION)).isEqualTo(
                1L);
        assertThat(metrics.getErrorsByCode()).containsEntry(DeploymentErrorCode.ARTIFACT_DIGEST_MISMATCH, 1L);
    }

    @Test
    void rejectsSemanticallyInvalidOutcomeReasonPairs() {
        ProcessDeploymentMetrics metrics = new ProcessDeploymentMetrics();

        assertThatThrownBy(() -> metrics.recordRuntimeInstall(AttemptOutcome.SUCCESS, RuntimeInstallReason.RUNTIME_LOAD))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> metrics.recordAliasConvergence(AttemptOutcome.FAILURE, AliasConvergenceReason.NONE))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
