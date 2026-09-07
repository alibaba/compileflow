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
package com.alibaba.compileflow.examples.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessRef;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = "jdbc:postgresql:.+")
class DeploymentExampleApplicationTest {
    @Autowired
    private ReleaseLifecycleService service;
    @Autowired
    private ProcessEngine processEngine;

    @Test
    @Timeout(60)
    void executesImmutableCanaryPromotionAbortAndRollbackLifecycle() {
        ReleaseLifecycleReport report = service.run();

        assertThat(report.exactV1().selectedVersion()).isEqualTo("v1");
        assertThat(report.initialStable().marker()).isEqualTo("v1");
        assertThat(report.canaryStable().selectedVersion()).isEqualTo("v1");
        assertThat(report.canaryCandidate().selectedVersion()).isEqualTo("v2");
        assertThat(report.canaryStable().routingKey()).isNotEqualTo(report.canaryCandidate().routingKey());
        assertThat(report.promoted().marker()).isEqualTo("v2");
        assertThat(report.aborted().marker()).isEqualTo("v2");
        assertThat(report.rolledBack().marker()).isEqualTo("v1");
        assertThat(report.exactV2AfterRollback().marker()).isEqualTo("v2");
        assertThat(processEngine
            .execute(ProcessRef.alias("default", report.processCode(), "production"), Map.of())
            .orElseThrow())
            .containsEntry("version_marker", "v1");
        assertThat(processEngine
            .execute(ProcessRef.version("default", report.processCode(), "v2"), Map.of())
            .isSuccess())
            .as("Temporary exact-version ownership must be released")
            .isFalse();

        Map<String, ReleaseLifecycleReport.RolloutObservation> rollouts = report
            .rollouts()
            .stream()
            .collect(Collectors.toMap(ReleaseLifecycleReport.RolloutObservation::operation, Function.identity()));
        assertThat(rollouts.get("canary-created").candidateWeightBps()).isEqualTo(1_000);
        assertThat(rollouts.get("canary-widened").candidateWeightBps()).isEqualTo(5_000);
        assertThat(rollouts.get("promoted").phase()).isEqualTo("COMPLETED");
        assertThat(rollouts.get("aborted").phase()).isEqualTo("ABORTED");
        assertThat(rollouts.get("rollback").phase()).isEqualTo("COMPLETED");
        assertThat(rollouts.values()).allSatisfy(rollout -> assertThat(rollout.auditEventCount()).isPositive());
    }
}
