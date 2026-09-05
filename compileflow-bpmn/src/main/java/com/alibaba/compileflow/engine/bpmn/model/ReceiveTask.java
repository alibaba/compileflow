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
package com.alibaba.compileflow.engine.bpmn.model;

import com.alibaba.compileflow.engine.core.model.TriggerEntryElement;

/**
 * BPMN receive activity with a message-backed wait semantic.
 *
 * <p>Execution targets decide how to realize the wait. ProcessEngine execution exposes a named trigger
 * entry; Durable execution persists and correlates the suspension.
 *
 * @author yusu
 */
public class ReceiveTask extends Activity implements TriggerEntryElement {
    private String messageRef;

    public String getMessageRef() {
        return messageRef;
    }

    public void setMessageRef(String messageRef) {
        this.messageRef = messageRef;
    }
}
