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

/**
 * Stable, bounded categories for deployment failures.
 *
 * <p>The code is suitable for transport error mapping and low-cardinality metrics. Human-readable
 * exception messages provide detail but are not a machine protocol.
 *
 * @author yusu
 */
public enum DeploymentErrorCode {
    /**
     * A command or protocol payload violated the public deployment contract.
     */
    INVALID_ARGUMENT,
    /**
     * An immutable version identity already exists with different content.
     */
    VERSION_CONFLICT,
    /**
     * Resolved artifact identity differs from the immutable version requested by the runtime.
     */
    ARTIFACT_IDENTITY_MISMATCH,
    /**
     * Artifact definition or Process-call bindings differ from their declared SHA-256 digest.
     */
    ARTIFACT_DIGEST_MISMATCH,
    /**
     * The requested immutable process version does not exist.
     */
    VERSION_NOT_FOUND,
    /**
     * An exact child-process version required by a publication does not exist.
     */
    DEPENDENCY_NOT_FOUND,
    /**
     * Requested rollout id does not exist.
     */
    ROLLOUT_NOT_FOUND,
    /**
     * A rollout command is incompatible with the current rollout or Alias state.
     */
    ROLLOUT_CONFLICT,
    /**
     * An idempotency key was reused with a different request.
     */
    IDEMPOTENCY_CONFLICT,
    /**
     * Durable deployment state could not be read or written.
     */
    REPOSITORY_ERROR,
    /**
     * An optimistic-lock or Alias-revision precondition failed.
     */
    CONCURRENT_MODIFICATION,
    /**
     * A published Alias did not converge to usable node-local state.
     */
    CONVERGENCE_FAILED,
    /**
     * The immutable artifact could not be published to its configured transport.
     */
    ARTIFACT_PROJECTION_FAILED,
    /**
     * An unexpected implementation failure escaped a deployment boundary.
     */
    INTERNAL_ERROR
}
