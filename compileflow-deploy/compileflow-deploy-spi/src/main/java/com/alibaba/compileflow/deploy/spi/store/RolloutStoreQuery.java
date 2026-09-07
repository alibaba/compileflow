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
package com.alibaba.compileflow.deploy.spi.store;

import com.alibaba.compileflow.deploy.api.rollout.RolloutPhase;

/**
 * Provider-neutral rollout-history query after public cursor decoding and validation.
 *
 * @param namespace optional namespace filter
 * @param code optional process-code filter
 * @param keyword optional case-insensitive process-code or rollout-id search term
 * @param alias optional Alias filter
 * @param phase optional rollout phase
 * @param cursor optional decoded keyset boundary
 * @param limit maximum rows to return, including an optional look-ahead row
 *
 * @author yusu
 */
public record RolloutStoreQuery(String namespace, String code, String keyword, String alias, RolloutPhase phase,
        RolloutPageKey cursor, int limit) {
    public RolloutStoreQuery {
        if (limit <= 0 || limit > 101) {
            throw new IllegalArgumentException("limit must be between 1 and 101");
        }
    }
}
