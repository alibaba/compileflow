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

/**
 * @author yusu
 */
public class InputOutputSpecification extends BaseElement {

    private List<DataInput> dataInputs;
    private List<DataOutput> dataOutputs;
    private List<InputSet> inputSets;
    private List<OutputSet> outputSets;

    public List<DataInput> getDataInputs() {
        return dataInputs;
    }

    public void setDataInputs(List<DataInput> dataInputs) {
        this.dataInputs = dataInputs;
    }

    public List<DataOutput> getDataOutputs() {
        return dataOutputs;
    }

    public void setDataOutputs(List<DataOutput> dataOutputs) {
        this.dataOutputs = dataOutputs;
    }

    public List<InputSet> getInputSets() {
        return inputSets;
    }

    public void setInputSets(List<InputSet> inputSets) {
        this.inputSets = inputSets;
    }

    public List<OutputSet> getOutputSets() {
        return outputSets;
    }

    public void setOutputSets(List<OutputSet> outputSets) {
        this.outputSets = outputSets;
    }

}
