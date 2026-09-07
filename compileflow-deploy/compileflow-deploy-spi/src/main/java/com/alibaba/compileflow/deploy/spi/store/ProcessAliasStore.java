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

import java.util.List;
import java.util.Optional;

/**
 * Read authority for committed published-alias state.
 *
 * @author yusu
 */
public interface ProcessAliasStore {
    long countDistinctProcesses(String namespace);

    Optional<ProcessAliasRecord> resolve(String namespace, String code, String alias);

    List<ProcessKey> listDistinctProcesses();

    List<ProcessAliasRecord> listAll(String namespace, String code);

    List<ProcessAliasRecord> list(String namespace, String code, String sortBy, boolean ascending, int limit,
            int offset);
}
