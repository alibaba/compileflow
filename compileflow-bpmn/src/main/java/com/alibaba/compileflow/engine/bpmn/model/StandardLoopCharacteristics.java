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
 * Standard while/until loop characteristics for a BPMN activity.
 *
 * @author yusu
 */
public class StandardLoopCharacteristics extends LoopCharacteristics {
    private String loopCondition;
    private Boolean testBefore;
    private Long loopMaximum;

    public String getLoopCondition() {
        return loopCondition;
    }

    public void setLoopCondition(String loopCondition) {
        this.loopCondition = loopCondition;
    }

    public Boolean getTestBefore() {
        return testBefore;
    }

    public void setTestBefore(Boolean testBefore) {
        this.testBefore = testBefore;
    }

    public Long getLoopMaximum() {
        return loopMaximum;
    }

    public void setLoopMaximum(Long loopMaximum) {
        this.loopMaximum = loopMaximum;
    }
}
