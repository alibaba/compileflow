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

import java.math.BigInteger;
import java.util.List;

public abstract class Activity extends FlowNode {

    private InputOutputSpecification ioSpecification;
    private List<Property> properties;
    private List<DataInputAssociation> dataInputAssociations;
    private List<DataOutputAssociation> dataOutputAssociations;
    private List<ResourceRole> resourceRoles;
    private LoopCharacteristics loopCharacteristics;
    private Boolean isForCompensation;
    private BigInteger startQuantity;
    private BigInteger completionQuantity;
    private String defaultFlow;

    public InputOutputSpecification getIoSpecification() {
        return ioSpecification;
    }

    public void setIoSpecification(InputOutputSpecification ioSpecification) {
        this.ioSpecification = ioSpecification;
    }

    public List<Property> getProperties() {
        return properties;
    }

    public void setProperties(List<Property> properties) {
        this.properties = properties;
    }

    public List<DataInputAssociation> getDataInputAssociations() {
        return dataInputAssociations;
    }

    public void setDataInputAssociations(
        List<DataInputAssociation> dataInputAssociations) {
        this.dataInputAssociations = dataInputAssociations;
    }

    public List<DataOutputAssociation> getDataOutputAssociations() {
        return dataOutputAssociations;
    }

    public void setDataOutputAssociations(
        List<DataOutputAssociation> dataOutputAssociations) {
        this.dataOutputAssociations = dataOutputAssociations;
    }

    public List<ResourceRole> getResourceRoles() {
        return resourceRoles;
    }

    public void setResourceRoles(List<ResourceRole> resourceRoles) {
        this.resourceRoles = resourceRoles;
    }

    public LoopCharacteristics getLoopCharacteristics() {
        return loopCharacteristics;
    }

    public void setLoopCharacteristics(LoopCharacteristics loopCharacteristics) {
        this.loopCharacteristics = loopCharacteristics;
    }

    public Boolean getForCompensation() {
        return isForCompensation;
    }

    public void setForCompensation(Boolean forCompensation) {
        isForCompensation = forCompensation;
    }

    public BigInteger getStartQuantity() {
        return startQuantity;
    }

    public void setStartQuantity(BigInteger startQuantity) {
        this.startQuantity = startQuantity;
    }

    public BigInteger getCompletionQuantity() {
        return completionQuantity;
    }

    public void setCompletionQuantity(BigInteger completionQuantity) {
        this.completionQuantity = completionQuantity;
    }

    public String getDefaultFlow() {
        return defaultFlow;
    }

    public void setDefaultFlow(String defaultFlow) {
        this.defaultFlow = defaultFlow;
    }

}
