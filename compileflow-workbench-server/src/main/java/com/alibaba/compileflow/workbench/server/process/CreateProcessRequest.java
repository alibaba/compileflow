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

import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessText;
import com.alibaba.compileflow.workbench.server.api.validation.RequestValidationException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Process creation request.
 *
 * @param code        unique process code
 * @param name        display name
 * @param type        BPMN or TBBPM model type
 * @param xml         optional process definition
 * @param description optional description
 * @param tags        optional tags
 * @author yusu
 */
public record CreateProcessRequest(@NotBlank String code, @NotBlank String name, @NotNull ProcessModelType type,
        String xml, String description, List<String> tags) {
    static final int MAX_NAME_LENGTH = 256;
    static final int MAX_DESCRIPTION_LENGTH = 1024;
    static final int MAX_TAG_COUNT = 32;
    static final int MAX_TAG_LENGTH = 64;

    public CreateProcessRequest {
        code = normalizeCode(code);
        name = normalizeBoundedIdentity(name, "name", MAX_NAME_LENGTH);
        xml = normalizeSource(xml);
        description = normalizeBoundedText(description, "description", MAX_DESCRIPTION_LENGTH);
        tags = normalizeTags(tags);
    }

    static List<String> normalizeTags(List<String> values) {
        if (values == null) {
            return List.of();
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String value : values) {
            String tag = normalizeBoundedIdentity(value, "tag", MAX_TAG_LENGTH);
            if (tag != null) {
                distinct.add(tag);
            }
        }
        if (distinct.size() > MAX_TAG_COUNT) {
            throw new RequestValidationException("tags must not contain more than " + MAX_TAG_COUNT + " values");
        }
        return List.copyOf(distinct);
    }

    static String normalizeBoundedText(String value, String name, int maxLength) {
        if (value == null) {
            return null;
        }
        requireValidUnicode(value, name);
        String normalized = ProcessText.strip(value);
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.codePointCount(0, normalized.length()) > maxLength) {
            throw new RequestValidationException(name + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }

    static String normalizeBoundedIdentity(String value, String name, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = ProcessText.strip(value);
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return ProcessIdentifiers.requireExactIdentity(normalized, name, maxLength);
        } catch (IllegalArgumentException exception) {
            throw new RequestValidationException(exception.getMessage(), exception);
        }
    }

    static String normalizeCode(String value) {
        if (value == null) {
            return null;
        }
        String normalized = ProcessText.strip(value);
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return ProcessIdentifiers.requireCode(normalized);
        } catch (IllegalArgumentException exception) {
            throw new RequestValidationException(exception.getMessage(), exception);
        }
    }

    static String normalizeSource(String value) {
        if (value == null) {
            return null;
        }
        requireValidUnicode(value, "xml");
        return ProcessText.strip(value).isEmpty() ? null : value;
    }

    static String requireValidUnicode(String value, String name) {
        try {
            return ProcessText.requireUnicode(value, name);
        } catch (IllegalArgumentException exception) {
            throw new RequestValidationException(exception.getMessage(), exception);
        }
    }

    private static String require(String value, String name) {
        if (value == null) {
            throw new RequestValidationException(name + " is required");
        }
        return value;
    }

    /**
     * Requires the process code.
     *
     * @return non-blank process code
     */
    public String requireCode() {
        return require(code, "code");
    }

    /**
     * Requires the process name.
     *
     * @return non-blank process name
     */
    public String requireName() {
        return require(name, "name");
    }

    /**
     * Requires a supported model type.
     *
     * @return BPMN or TBBPM
     */
    public ProcessModelType requireType() {
        if (type == null) {
            throw new RequestValidationException("type is required");
        }
        return type;
    }
}
