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

/**
 * Deployment rollback compare-and-set request.
 *
 * @param expectedRouteRevision expected current route revision
 * @author yusu
 */
public record RollbackDeploymentRequest(@NotNull Long expectedRouteRevision) {
    /**
     * Returns a validated positive route revision.
     *
     * @return expected route revision
     */
    public long requireExpectedRouteRevision() {
        if (expectedRouteRevision == null || expectedRouteRevision <= 0) {
            throw new IllegalArgumentException("expectedRouteRevision must be greater than 0");
        }
        return expectedRouteRevision;
    }
}
