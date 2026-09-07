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

import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.api.spi.ProcessArtifactSource;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Persistence authority for exact immutable process versions.
 *
 * @author yusu
 */
public interface ProcessVersionStore extends ProcessArtifactSource {
    long currentTimeMillis();

    ProcessVersionRecord save(ProcessVersionRecord record);

    Optional<ProcessVersionRecord> find(String namespace, String code, String version);

    List<ProcessVersionRecord> list(String namespace, String code, String versionPrefix, PublishedVersionPageKey cursor,
            int limit);

    @Override
    default Optional<ProcessArtifact> find(ProcessRef.Version ref) {
        ProcessRef.Version versionRef = Objects.requireNonNull(ref, "ref");
        return find(versionRef.namespace(), versionRef.code(), versionRef.version())
            .map(ProcessVersionRecord::toArtifact);
    }
}
