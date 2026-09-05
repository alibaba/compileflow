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
package com.alibaba.compileflow.workbench.server.status;

import com.alibaba.compileflow.workbench.server.config.CompileFlowWorkbenchServerProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes product-level Workbench availability separately from platform
 * liveness and readiness probes.
 *
 * @author yusu
 */
@RestController
@RequestMapping("/api/status")
public final class WorkbenchStatusController {
    private final boolean previewExecutionEnabled;

    public WorkbenchStatusController(CompileFlowWorkbenchServerProperties properties) {
        this.previewExecutionEnabled = properties.getPreviewExecution().isEnabled();
    }

    /**
     * Returns availability of the Workbench execution backend.
     *
     * @return stable browser-facing availability response
     */
    @GetMapping
    public WorkbenchStatusResponse getWorkbenchStatus() {
        if (previewExecutionEnabled) {
            return new WorkbenchStatusResponse(true, "Trusted draft execution is enabled");
        }
        return new WorkbenchStatusResponse(false, "Draft execution is disabled by server policy");
    }
}
