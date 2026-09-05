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
package com.alibaba.compileflow.deploy.api.rollout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One page of rollout records.
 *
 * @author yusu
 */
public final class RolloutPage {
    private final List<ProcessRollout> records;
    private final RolloutCursor nextCursor;

    /**
     * Creates an immutable rollout page.
     *
     * @param records  records in this page
     * @param nextCursor continuation cursor when more records exist, otherwise {@code null}
     */
    public RolloutPage(List<ProcessRollout> records, RolloutCursor nextCursor) {
        List<ProcessRollout> copy = new ArrayList<>(Objects.requireNonNull(records, "records"));
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("records must not contain null elements");
        }
        if (copy.size() > 100) {
            throw new IllegalArgumentException("records size must not exceed 100");
        }
        if (copy.isEmpty() && nextCursor != null) {
            throw new IllegalArgumentException("nextCursor requires at least one record");
        }
        this.records = Collections.unmodifiableList(copy);
        this.nextCursor = nextCursor;
    }

    /**
     * Returns the immutable page contents.
     *
     * @return rollout records
     */
    public List<ProcessRollout> getRecords() {
        return records;
    }

    /**
     * Returns the opaque continuation cursor when more records exist.
     *
     * @return next cursor, or {@code null}
     */
    public RolloutCursor getNextCursor() {
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
