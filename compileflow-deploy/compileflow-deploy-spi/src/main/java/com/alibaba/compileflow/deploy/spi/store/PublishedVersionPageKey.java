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

import com.alibaba.compileflow.engine.ProcessIdentifiers;

/**
 * Decoded keyset boundary for immutable published-version traversal.
 *
 * @param createdAt positive authority timestamp in epoch milliseconds
 * @param version exact version coordinate
 *
 * @author yusu
 */
public record PublishedVersionPageKey(long createdAt, String version) {
    public PublishedVersionPageKey {
        if (createdAt <= 0L) {
            throw new IllegalArgumentException("createdAt must be positive");
        }
        version = ProcessIdentifiers.requireVersion(version);
    }
}
