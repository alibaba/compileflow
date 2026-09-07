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
package com.alibaba.compileflow.deploy.runtime.version;

/**
 * Execution-scoped retention of one installed exact process runtime.
 *
 * <p>The runtime is ready when the lease is returned and remains installed until the lease is
 * closed. This contract covers deployment materialization; the engine still owns its shorter
 * execution-admission lease.
 *
 * @author yusu
 */
public interface VersionRuntimeLease extends AutoCloseable {
    /**
     * Idempotently releases the installation demand held by this lease.
     */
    @Override
    void close();
}
