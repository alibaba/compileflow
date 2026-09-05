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
package com.alibaba.compileflow.deploy.control.validation;

import com.alibaba.compileflow.deploy.api.artifact.ProcessCallBinding;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightReport;
import java.util.List;
import java.util.Objects;

/**
 * Completed publication validation and its source-derived exact call edges.
 *
 * @author yusu
 */
public record ProcessPublicationValidation(ProcessPreflightReport report, List<ProcessCallBinding> callBindings) {
    public ProcessPublicationValidation {
        report = Objects.requireNonNull(report, "report");
        callBindings = List.copyOf(Objects.requireNonNull(callBindings, "callBindings"));
    }
}
