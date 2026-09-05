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
package com.alibaba.compileflow.deploy.api.spi;

import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import java.util.Optional;

/**
 * Resolves immutable published artifacts from an authoritative content store.
 *
 * <p>This SPI is intentionally narrower than a control-plane repository. Data-plane runtimes
 * need exact source content and identity, but must not mutate publication or rollout state.
 *
 * <p>Implementations must be thread-safe because runtime workers may resolve artifacts
 * concurrently. Database and remote implementations must enforce bounded connection, query, and
 * read deadlines and preserve interruption. Runtime shutdown cannot forcibly terminate
 * non-cooperative external I/O. Lookup failures fail exact-version acquisition; no other source is
 * consulted implicitly. The application or dependency-injection container owns the source
 * lifecycle.
 *
 * @author yusu
 */
public interface ProcessArtifactSource {
    /**
     * Finds the exact immutable artifact identified by {@code ref}.
     *
     * @param ref published-version identity
     * @return non-null optional containing the exact artifact when the version exists
     */
    Optional<ProcessArtifact> find(ProcessRef.Version ref);
}
