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
package com.alibaba.compileflow.durable.api.model;

import java.util.List;
import java.util.Objects;

/**
 * One bounded keyset page of unresolved Wait, Timer, and Effect occurrences.
 *
 * @param items immutable active-work items
 * @param nextCursor continuation cursor, or {@code null} at the end
 *
 * @author yusu
 */
public record ActiveWorkPage(List<ActiveWork> items, ActiveWorkCursor nextCursor) {
    public ActiveWorkPage {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
    }
}
