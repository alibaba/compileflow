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
package com.alibaba.compileflow.engine.tbbpm.model;

/**
 * Defines a named entry for a later trigger invocation.
 *
 * <p>ProcessEngine execution ends the current invocation at this node and exposes a later trigger
 * entry. Durable execution persists the suspension and resumes the same Run when the wait completes.
 *
 * @author wuxiang
 * @author yusu
 */
public class WaitTaskNode extends TriggerEntryNode {
    private String timeout;

    /**
     * Optional ISO-8601 duration measured from the committed suspension time.
     */
    public String getTimeout() {
        return timeout;
    }

    public void setTimeout(String timeout) {
        this.timeout = timeout;
    }
}
