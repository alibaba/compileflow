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

import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.workbench.server.api.validation.RequestValidationException;
import com.alibaba.compileflow.workbench.server.api.validation.RequestValueParser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * Executes one explicit draft definition without publishing or routing it.
 *
 * @param code         process code used for compilation and attribution
 * @param modelType    BPMN or TBBPM model type
 * @param xml          UTF-8 XML definition text
 * @param invocationId optional caller-supplied correlation id
 * @param params       dynamic process variables
 * @author yusu
 */
public record PreviewExecutionRequest(
        @NotBlank @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*") String code,
        @NotNull ProcessModelType modelType, @NotBlank String xml,
        @Size(max = ProcessIdentifiers.MAX_INVOCATION_ID_LENGTH) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:@-]*") String invocationId,
        Map<String, Object> params) {
    public PreviewExecutionRequest {
        code = StringUtils.trimToNull(code);
        xml = StringUtils.isBlank(xml) ? null : xml;
        try {
            invocationId = RequestValueParser.optionalInvocationId(invocationId);
        } catch (IllegalArgumentException exception) {
            throw new RequestValidationException(exception.getMessage(), exception);
        }
        params = RequestValueParser.immutableMap(params);
    }
}
