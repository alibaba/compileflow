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
package com.alibaba.compileflow.engine.definition.tbbpm;

import com.alibaba.compileflow.engine.definition.common.VarSupport;
import com.alibaba.compileflow.engine.definition.common.var.IVar;

import java.util.ArrayList;
import java.util.List;

/**
 * @author wuxiang
 * @author yusu
 */
public class SubBpmNode extends ActivityNode implements VarSupport {

    private String subBpmCode;

    private String type;

    private boolean waitForCompletion;

    private boolean waitForTrigger;

    private List<IVar> vars = new ArrayList<>(8);

    public String getSubBpmCode() {
        return subBpmCode;
    }

    public void setSubBpmCode(String subBpmCode) {
        this.subBpmCode = subBpmCode;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isWaitForCompletion() {
        return waitForCompletion;
    }

    public void setWaitForCompletion(boolean waitForCompletion) {
        this.waitForCompletion = waitForCompletion;
    }

    public boolean isWaitForTrigger() {
        return waitForTrigger;
    }

    public void setWaitForTrigger(boolean waitForTrigger) {
        this.waitForTrigger = waitForTrigger;
    }

    @Override
    public List<IVar> getVars() {
        return vars;
    }

    @Override
    public void addVar(IVar var) {
        vars.add(var);
    }

}
