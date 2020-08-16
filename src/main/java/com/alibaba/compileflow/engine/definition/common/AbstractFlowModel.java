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
package com.alibaba.compileflow.engine.definition.common;

import com.alibaba.compileflow.engine.definition.common.var.IVar;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yusu
 */
public abstract class AbstractFlowModel<T extends Node> implements FlowModel<T> {

    private String id;

    private String code;

    private String name;

    private List<IVar> vars = new ArrayList<>();

    private List<IVar> paramVars = new ArrayList<>();

    private List<IVar> innerVars = new ArrayList<>();

    private List<IVar> returnVars = new ArrayList<>();

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public List<IVar> getVars() {
        return vars;
    }

    public void setVars(List<IVar> vars) {
        this.vars = vars;
    }

    @Override
    public List<IVar> getParamVars() {
        return paramVars;
    }

    public void setParamVars(List<IVar> paramVars) {
        this.paramVars = paramVars;
    }

    @Override
    public List<IVar> getInnerVars() {
        return innerVars;
    }

    public void setInnerVars(List<IVar> innerVars) {
        this.innerVars = innerVars;
    }

    @Override
    public List<IVar> getReturnVars() {
        return returnVars;
    }

    public void setReturnVars(List<IVar> returnVars) {
        this.returnVars = returnVars;
    }

    @Override
    public String getTag() {
        return "UNDEFINED";
    }

}
