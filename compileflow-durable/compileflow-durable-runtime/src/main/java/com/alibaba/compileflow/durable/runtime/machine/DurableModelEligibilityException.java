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
package com.alibaba.compileflow.durable.runtime.machine;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Compile-time rejection of a model outside the Durable semantic protocol.
 *
 * @author yusu
 */
public final class DurableModelEligibilityException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    private final DurableModelEligibility eligibility;

    public DurableModelEligibilityException(DurableModelEligibility eligibility) {
        super(message(Objects.requireNonNull(eligibility, "eligibility")));
        this.eligibility = eligibility;
    }

    private static String message(DurableModelEligibility eligibility) {
        return eligibility
            .problems()
            .stream()
            .map(problem -> problem.code() + "[" + (problem.nodeId() == null ? "process" : problem.nodeId()) + "]: "
                    + problem.message())
            .collect(Collectors.joining("; "));
    }

    public DurableModelEligibility getEligibility() {
        return eligibility;
    }
}
