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

public class StandardLoopCharacteristics extends LoopCharacteristics {

    private String loopCondition;
    private Boolean testBefore;
    private Long loopMaximum;

    private String collection;
    private String elementVarClass;
    private String elementVar;
    private String indexVar;

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

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public String getElementVarClass() {
        return elementVarClass;
    }

    public void setElementVarClass(String elementVarClass) {
        this.elementVarClass = elementVarClass;
    }

    public String getElementVar() {
        return elementVar;
    }

    public void setElementVar(String elementVar) {
        this.elementVar = elementVar;
    }

    public String getIndexVar() {
        return indexVar;
    }

    public void setIndexVar(String indexVar) {
        this.indexVar = indexVar;
    }

}
