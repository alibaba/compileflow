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

import java.util.List;

/**
 * One page of deployment views.
 *
 * @author yusu
 */
final class DeploymentPage {
    final List<DeploymentView> deployments;
    final String nextCursor;

    DeploymentPage(List<DeploymentView> deployments, String nextCursor) {
        this.deployments = deployments;
        this.nextCursor = nextCursor;
    }
}
