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

import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightOptions;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightReport;
import com.alibaba.compileflow.engine.ProcessEngine;
import java.io.Serial;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Validates explicit Process definitions without publishing or executing them.
 *
 * @author yusu
 */
@Service
public final class ProcessDefinitionPreflightService {
    private final ProcessEngine engine;

    public ProcessDefinitionPreflightService(ProcessEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    public ProcessPreflightReport preflight(String code, ProcessModelType modelType, String xmlContent) {
        ProcessDefinition definition;
        try {
            definition = ProcessDefinition.inline(modelType, code, xmlContent);
        } catch (IllegalArgumentException exception) {
            throw new InvalidProcessDefinitionException(exception.getMessage(), exception);
        }
        return engine.tooling().preflight(definition, ProcessPreflightOptions.strict());
    }

    static final class InvalidProcessDefinitionException extends IllegalArgumentException {
        @Serial
        private static final long serialVersionUID = 1L;

        InvalidProcessDefinitionException(String message, IllegalArgumentException cause) {
            super(message, cause);
        }
    }
}
