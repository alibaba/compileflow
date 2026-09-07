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
package com.alibaba.compileflow.workbench.server.execution;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Product-private semantic persistence boundary for asynchronous invocation ownership and attempts.
 *
 * <p>The selected database provider owns database time, fencing, and every transaction that spans the invocation
 * aggregate and its physical attempt ledger.</p>
 *
 * @author yusu
 */
interface AsyncInvocationStore {
    String OUTCOME_RUNNING = "running";
    String OUTCOME_SUCCEEDED = "succeeded";
    String OUTCOME_FAILED = "failed";
    String OUTCOME_LEASE_EXPIRED = "lease_expired";
    String DISPOSITION_SUCCEEDED = "succeeded";
    String DISPOSITION_RETRY_SCHEDULED = "retry_scheduled";
    String DISPOSITION_DEAD_LETTERED = "dead_lettered";

    long currentTimeMillis();

    void insert(AsyncInvocationEntity invocation);

    Optional<Claim> claim(String invocationId, String workerId, long leaseDurationMs);

    Set<String> renewLeases(Map<String, String> authorities, String runningStatus, long extensionMillis);

    int pinRouting(String invocationId, String runningStatus, String leaseToken, String routingJson);

    int complete(Claim claim, String routingJson, String resultJson, String traceId, long durationMs);

    int fail(Claim claim, String routingJson, String resultJson, String errorCode, String errorMessage, String traceId,
            Long durationMs, boolean permanent);

    int recoverExpired(AsyncInvocationEntity candidate, String errorCode, String errorMessage);

    int requeueDeadLetter(String invocationId);

    Optional<AttemptPage> listAttempts(String invocationId, long afterSequence, int limit);

    record Claim(AsyncInvocationEntity invocation, String attemptId, String leaseToken) {
        public Claim {
            Objects.requireNonNull(invocation, "invocation");
            Objects.requireNonNull(attemptId, "attemptId");
            Objects.requireNonNull(leaseToken, "leaseToken");
        }
    }

    record AttemptPage(List<AsyncInvocationAttemptEntity> data, boolean hasMore, Long nextAfterSequence) {}
}
