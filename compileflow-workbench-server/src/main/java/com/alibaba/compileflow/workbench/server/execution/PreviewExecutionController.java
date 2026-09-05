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

import com.alibaba.compileflow.workbench.server.api.problem.ApiProblemException;
import com.alibaba.compileflow.workbench.server.api.problem.RedactedFailure;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP boundary for non-persistent Workbench definition previews.
 *
 * @author yusu
 */
@RestController
@RequestMapping("/api/executions/preview")
@ConditionalOnProperty(prefix = "compileflow.workbench.server.preview-execution", name = "enabled", havingValue = "true")
public final class PreviewExecutionController {
    private static final Logger LOGGER = LoggerFactory.getLogger(PreviewExecutionController.class);
    private final PreviewExecutionService service;

    public PreviewExecutionController(PreviewExecutionService service) {
        this.service = service;
    }

    /**
     * Executes an explicit trusted draft definition without publishing it.
     *
     * <p>This is real server-side execution, not a sandbox or side-effect-free validation.
     *
     * @param request required preview request
     * @return structured process outcome
     */
    @PostMapping
    public ProcessExecutionResponse executePreview(@Valid @RequestBody PreviewExecutionRequest request) {
        if (request == null) {
            throw ApiProblemException.invalidRequest("request body is required");
        }
        try {
            return service.execute(request.code(), request.modelType(), request.xml(), request.invocationId(),
                    request.params());
        } catch (Exception exception) {
            LOGGER.error("Unexpected preview execution failure: code={}", request.code(),
                    RedactedFailure.forLogging(exception));
            throw ApiProblemException.internalError("Internal preview execution error");
        }
    }
}
