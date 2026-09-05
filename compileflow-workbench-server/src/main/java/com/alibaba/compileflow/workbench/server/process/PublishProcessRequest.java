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

import com.alibaba.compileflow.deploy.api.release.ReleaseMetadata;
import jakarta.validation.constraints.NotNull;

/**
 * Process publication metadata request.
 *
 * @param changelog        optional release description
 * @param expectedRevision revision of the draft snapshot to publish
 * @author yusu
 */
public record PublishProcessRequest(String changelog, @NotNull Long expectedRevision) {
    private static final int MAX_CHANGELOG_LENGTH = ReleaseMetadata.MAX_VALUE_CHARACTERS;

    public PublishProcessRequest {
        changelog = CreateProcessRequest.normalizeBoundedText(changelog, "changelog", MAX_CHANGELOG_LENGTH);
    }

    /**
     * Returns the draft revision required for publication.
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
