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

import java.util.List;

public record ReleaseLifecycleReport(String processCode, ExecutionObservation exactV1,
        ExecutionObservation initialStable, ExecutionObservation canaryStable, ExecutionObservation canaryCandidate,
        ExecutionObservation promoted, ExecutionObservation aborted, ExecutionObservation rolledBack,
        ExecutionObservation exactV2AfterRollback, List<RolloutObservation> rollouts) {
    record ExecutionObservation(String requested, String selectedVersion, String marker, String routingKey) {}

    record RolloutObservation(String operation, String rolloutId, String phase, long aliasRevision, long rolloutRevision,
            int candidateWeightBps, int auditEventCount) {}
}
