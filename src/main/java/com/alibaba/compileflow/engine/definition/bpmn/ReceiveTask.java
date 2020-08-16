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

import com.alibaba.compileflow.engine.definition.common.action.HasInOutAction;
import com.alibaba.compileflow.engine.definition.common.action.IAction;

public class ReceiveTask extends Task implements HasInOutAction {

    private String implementation;

    private boolean instantiate;

    private String messageRef;

    private String operationRef;

    /**
     * extension
     */
    private IAction inAction;

    private IAction outAction;

    public String getImplementation() {
        return implementation;
    }

    public void setImplementation(String implementation) {
        this.implementation = implementation;
    }

    public boolean isInstantiate() {
        return instantiate;
    }

    public void setInstantiate(boolean instantiate) {
        this.instantiate = instantiate;
    }

    public String getMessageRef() {
        return messageRef;
    }

    public void setMessageRef(String messageRef) {
        this.messageRef = messageRef;
    }

    public String getOperationRef() {
        return operationRef;
    }

    public void setOperationRef(String operationRef) {
        this.operationRef = operationRef;
    }

    @Override
    public IAction getInAction() {
        return inAction;
    }

    public void setInAction(IAction inAction) {
        this.inAction = inAction;
    }

    @Override
    public IAction getOutAction() {
        return outAction;
    }

    public void setOutAction(IAction outAction) {
        this.outAction = outAction;
    }

}
