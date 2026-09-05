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

import com.alibaba.compileflow.workbench.server.api.validation.RequestValidationException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Editable process update request.
 *
 * @param name             replacement display name
 * @param xml              replacement process definition
 * @param description      optional description
 * @param tags             replacement tags
 * @param expectedRevision revision of the draft being replaced
 * @author yusu
 */
public record UpdateProcessRequest(@NotBlank String name, @NotNull String xml, String description,
        @NotNull List<String> tags, @NotNull Long expectedRevision) {
    public UpdateProcessRequest {
        name = CreateProcessRequest.normalizeBoundedIdentity(name, "name", CreateProcessRequest.MAX_NAME_LENGTH);
        if (xml != null) {
            xml = CreateProcessRequest.requireValidUnicode(xml, "xml");
        }
        description = CreateProcessRequest.normalizeBoundedText(description, "description",
                CreateProcessRequest.MAX_DESCRIPTION_LENGTH);
        if (tags == null) {
            throw new RequestValidationException("tags is required");
        }
        tags = CreateProcessRequest.normalizeTags(tags);
    }

    /**
     * Returns the required replacement name.
     *
     * @return non-blank process name
     */
    public String requireName() {
        if (name == null) {
            throw new IllegalArgumentException("name is required");
        }
        return name;
    }

    /**
     * Returns the required replacement XML.
     *
     * @return process XML, which may be empty for an unfinished draft
     */
    public String requireXml() {
        if (xml == null) {
            throw new IllegalArgumentException("xml is required");
        }
        return xml;
    }

    /**
     * Returns the draft revision required for compare-and-set.
     *
     * @return non-negative expected revision
     */
    public long requireExpectedRevision() {
        if (expectedRevision == null || expectedRevision < 0L) {
            throw new IllegalArgumentException("expectedRevision must be a non-negative integer");
        }
        return expectedRevision;
    }
}
