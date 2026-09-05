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
package com.alibaba.compileflow.workbench.server.deployment;

import jakarta.validation.constraints.NotNull;
import org.apache.commons.lang3.StringUtils;

/**
 * Canary abort compare-and-set request.
 *
 * @param expectedRevision expected rollout revision
 * @param reason           optional operator reason
 * @author yusu
 */
public record AbortCanaryRequest(@NotNull Long expectedRevision, String reason) {
    public AbortCanaryRequest {
        reason = StringUtils.trimToNull(reason);
    }

    /**
     * Returns a validated positive rollout revision.
     *
     * @return expected rollout revision
     */
    public long requireExpectedRevision() {
        return DeploymentRequestValidation.requirePositiveRevision(expectedRevision);
    }
}
