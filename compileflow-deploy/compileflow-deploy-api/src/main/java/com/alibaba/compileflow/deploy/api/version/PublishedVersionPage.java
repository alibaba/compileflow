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
package com.alibaba.compileflow.deploy.api.version;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One page of immutable published process versions.
 *
 * @author yusu
 */
public final class PublishedVersionPage {
    private final List<PublishedProcessVersion> versions;
    private final PublishedVersionCursor nextCursor;

    /**
     * Creates an immutable published-version page.
     *
     * @param versions versions in this page
     * @param nextCursor continuation cursor when more versions exist, otherwise {@code null}
     */
    public PublishedVersionPage(List<PublishedProcessVersion> versions, PublishedVersionCursor nextCursor) {
        List<PublishedProcessVersion> copy = new ArrayList<>(Objects.requireNonNull(versions, "versions"));
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("versions must not contain null elements");
        }
        if (copy.size() > 100) {
            throw new IllegalArgumentException("versions size must not exceed 100");
        }
        if (copy.isEmpty() && nextCursor != null) {
            throw new IllegalArgumentException("nextCursor requires at least one version");
        }
        this.versions = Collections.unmodifiableList(copy);
        this.nextCursor = nextCursor;
    }

    /**
     * Returns the immutable page contents.
     *
     * @return published versions
     */
    public List<PublishedProcessVersion> getVersions() {
        return versions;
    }

    /**
     * Returns the opaque continuation cursor when more versions exist.
     *
     * @return next cursor, or {@code null}
     */
    public PublishedVersionCursor getNextCursor() {
        return nextCursor;
    }

    /**
     * Reports whether another page can be requested.
     *
     * @return {@code true} when {@link #getNextCursor()} is non-null
     */
    public boolean hasMore() {
        return nextCursor != null;
    }
}
