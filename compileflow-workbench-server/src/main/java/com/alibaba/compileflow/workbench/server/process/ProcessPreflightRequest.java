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
package com.alibaba.compileflow.workbench.server.process;

import com.alibaba.compileflow.engine.ProcessModelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Preflight request for one inline process definition.
 *
 * @param code      process code
 * @param modelType BPMN or TBBPM model type
 * @param xml       UTF-8 XML definition text
 * @author yusu
 */
public record ProcessPreflightRequest(@NotBlank String code, @NotNull ProcessModelType modelType, @NotBlank String xml) {
    public ProcessPreflightRequest {
        code = CreateProcessRequest.normalizeCode(code);
        xml = CreateProcessRequest.normalizeSource(xml);
    }

    private static void require(String value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    /**
     * Validates fields when the request is used outside Spring MVC validation.
     */
    public void validate() {
        require(code, "code");
        require(xml, "xml");
        if (modelType == null) {
            throw new IllegalArgumentException("modelType is required");
        }
    }
}
