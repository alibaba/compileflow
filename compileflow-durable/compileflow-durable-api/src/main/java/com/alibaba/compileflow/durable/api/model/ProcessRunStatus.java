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

/**
 * Stable product-level lifecycle of a Durable Process Run.
 *
 * <p>Runnable and running states expose the scheduling distinction needed by operators without
 * exposing lease owners, tokens, or storage-specific state.</p>
 *
 * @author yusu
 */
public enum ProcessRunStatus {
    RUNNABLE(false),
    RUNNING(false),
    WAITING(false),
    SUCCEEDED(true),
    FAILED(true),
    CANCELLED(true);
    private final boolean terminal;

    ProcessRunStatus(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
