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

import com.alibaba.compileflow.engine.core.model.AbstractFlowElement;
import com.alibaba.compileflow.engine.core.model.Transition;

/**
 * Directed edge connecting two nodes in a BPMN process graph.
 *
 * @author yusu
 */
public class SequenceFlow extends AbstractFlowElement implements Transition {
    private String condition;
    private String source;
    private String target;

    public void setCondition(String condition) {
        this.condition = condition;
    }

    @Override
    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    @Override
    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    @Override
    public String getCondition() {
        return condition;
    }
}
