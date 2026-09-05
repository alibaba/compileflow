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

import jakarta.validation.constraints.NotBlank;

/**
 * Process duplication request.
 *
 * @param newCode target process code
 * @param newName target display name
 * @author yusu
 */
public record DuplicateProcessRequest(@NotBlank String newCode, @NotBlank String newName) {
    public DuplicateProcessRequest {
        newCode = CreateProcessRequest.normalizeCode(newCode);
        newName = CreateProcessRequest.normalizeBoundedIdentity(newName, "newName", CreateProcessRequest.MAX_NAME_LENGTH);
    }

    private static String require(String value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    /**
     * Requires the target process code.
     *
     * @return non-blank target code
     */
    public String requireNewCode() {
        return require(newCode, "newCode");
    }

    /**
     * Requires the target process name.
     *
     * @return non-blank target name
     */
    public String requireNewName() {
        return require(newName, "newName");
    }
}
