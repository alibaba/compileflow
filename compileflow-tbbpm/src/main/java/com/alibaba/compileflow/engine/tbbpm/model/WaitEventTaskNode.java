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
 * Defines a named wait semantic guarded by an event selector.
 *
 * <p>Execution targets decide how to realize the wait: an ProcessEngine runtime exposes a trigger
 * entry, while a Durable runtime can persist and correlate the suspension.
 *
 * @author wuxiang
 */
public class WaitEventTaskNode extends TriggerEntryNode {
    private String event;
    private String timeout;

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

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
