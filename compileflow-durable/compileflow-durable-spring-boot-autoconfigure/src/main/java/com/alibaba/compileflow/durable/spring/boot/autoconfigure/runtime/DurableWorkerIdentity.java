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
package com.alibaba.compileflow.durable.spring.boot.autoconfigure.runtime;

import com.alibaba.compileflow.durable.api.validation.DurableIdentifiers;
import java.util.UUID;

/**
 * One process-local Durable worker identity shared by every execution kind.
 *
 * @author yusu
 */
public record DurableWorkerIdentity(String value) {
    public DurableWorkerIdentity {
        value = DurableIdentifiers.requireIdentity(value, "workerIdentity", 96);
    }

    public static DurableWorkerIdentity create(String configuredId) {
        String value = configuredId == null
                ? "durable-" + ProcessHandle.current().pid() + '-' + UUID.randomUUID().toString().substring(0, 12)
                : configuredId;
        return new DurableWorkerIdentity(value);
    }

    public String worker(String kind) {
        return DurableIdentifiers.requireIdentity(value + '/' + kind, "workerId", 128);
    }
}
