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
package com.alibaba.compileflow.deploy.testkit;

import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.api.spi.ProcessArtifactSource;
import com.alibaba.compileflow.deploy.spi.store.ProcessVersionRecord;

final class InMemoryProcessArtifactSourceContractTest extends ProcessArtifactSourceContract {
    @Override
    protected ProcessArtifactSource createSource(ProcessArtifact artifact) {
        InMemoryProcessVersionStore store = new InMemoryProcessVersionStore();
        store.save(ProcessVersionRecord
            .builder()
            .namespace(artifact.getRef().namespace())
            .code(artifact.getRef().code())
            .version(artifact.getRef().version())
            .processDefinition(artifact.getDefinition())
            .artifactDigest(artifact.getArtifactDigest())
            .callBindings(java.util.List.copyOf(artifact.getCallBindings().values()))
            .actor("contract-test")
            .createdAt(1L)
            .build());
        return store;
    }
}
