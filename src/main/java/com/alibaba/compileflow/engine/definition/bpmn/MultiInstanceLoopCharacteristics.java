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
package com.alibaba.compileflow.engine.definition.bpmn;

import java.util.List;

public class MultiInstanceLoopCharacteristics extends LoopCharacteristics {

    private Expression loopCardinality;
    private String loopDataInputRef;
    private String loopDataOutputRef;
    private DataInput inputDataItem;
    private DataOutput outputDataItem;
    private List<ComplexBehaviorDefinition> complexBehaviorDefinition;
    private Expression completionCondition;
    private Boolean isSequential;
    private MultiInstanceFlowCondition behavior;
    private String oneBehaviorEventRef;
    private String noneBehaviorEventRef;

    public Expression getLoopCardinality() {
        return loopCardinality;
    }

    public void setLoopCardinality(Expression loopCardinality) {
        this.loopCardinality = loopCardinality;
    }

    public String getLoopDataInputRef() {
        return loopDataInputRef;
    }

    public void setLoopDataInputRef(String loopDataInputRef) {
        this.loopDataInputRef = loopDataInputRef;
    }

    public String getLoopDataOutputRef() {
        return loopDataOutputRef;
    }

    public void setLoopDataOutputRef(String loopDataOutputRef) {
        this.loopDataOutputRef = loopDataOutputRef;
    }

    public DataInput getInputDataItem() {
        return inputDataItem;
    }

    public void setInputDataItem(DataInput inputDataItem) {
        this.inputDataItem = inputDataItem;
    }

    public DataOutput getOutputDataItem() {
        return outputDataItem;
    }

    public void setOutputDataItem(DataOutput outputDataItem) {
        this.outputDataItem = outputDataItem;
    }

    public List<ComplexBehaviorDefinition> getComplexBehaviorDefinition() {
        return complexBehaviorDefinition;
    }

    public void setComplexBehaviorDefinition(
        List<ComplexBehaviorDefinition> complexBehaviorDefinition) {
        this.complexBehaviorDefinition = complexBehaviorDefinition;
    }

    public Expression getCompletionCondition() {
        return completionCondition;
    }

    public void setCompletionCondition(Expression completionCondition) {
        this.completionCondition = completionCondition;
    }

    public Boolean getSequential() {
        return isSequential;
    }

    public void setSequential(Boolean sequential) {
        isSequential = sequential;
    }

    public MultiInstanceFlowCondition getBehavior() {
        return behavior;
    }

    public void setBehavior(MultiInstanceFlowCondition behavior) {
        this.behavior = behavior;
    }

    public String getOneBehaviorEventRef() {
        return oneBehaviorEventRef;
    }

    public void setOneBehaviorEventRef(String oneBehaviorEventRef) {
        this.oneBehaviorEventRef = oneBehaviorEventRef;
    }

    public String getNoneBehaviorEventRef() {
        return noneBehaviorEventRef;
    }

    public void setNoneBehaviorEventRef(String noneBehaviorEventRef) {
        this.noneBehaviorEventRef = noneBehaviorEventRef;
    }

}
