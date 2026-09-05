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

/**
 * Executable BPMN intermediate catch event with one supported event definition.
 *
 * @author yusu
 */
public final class IntermediateCatchEvent extends TriggerEntryNode {
    private MessageEventDefinition messageEventDefinition;
    private TimerEventDefinition timerEventDefinition;

    public MessageEventDefinition getMessageEventDefinition() {
        return messageEventDefinition;
    }

    public void setMessageEventDefinition(MessageEventDefinition messageEventDefinition) {
        this.messageEventDefinition = messageEventDefinition;
    }

    public TimerEventDefinition getTimerEventDefinition() {
        return timerEventDefinition;
    }

    public void setTimerEventDefinition(TimerEventDefinition timerEventDefinition) {
        this.timerEventDefinition = timerEventDefinition;
    }
}
