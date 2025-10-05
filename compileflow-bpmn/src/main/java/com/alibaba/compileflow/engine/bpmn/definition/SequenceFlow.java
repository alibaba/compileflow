/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.bpmn.definition;

import com.alibaba.compileflow.engine.core.definition.BaseFlowElement;
import com.alibaba.compileflow.engine.core.definition.Transition;

/**
 * @author yusu
 */
public class SequenceFlow extends BaseFlowElement implements Transition {

    private String conditionExpression;

    private String source;

    private String target;

    private Boolean isImmediate;

    private int priority = 0;

    public void setConditionExpression(String conditionExpression) {
        this.conditionExpression = conditionExpression;
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

    public Boolean getImmediate() {
        return isImmediate;
    }

    public void setImmediate(Boolean immediate) {
        isImmediate = immediate;
    }

    @Override
    public String getExpression() {
        return conditionExpression;
    }

    public void setExpression(String expression) {
        this.conditionExpression = expression;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

}
