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
package com.alibaba.compileflow.engine.tbbpm.definition;

import com.alibaba.compileflow.engine.core.definition.Element;
import com.alibaba.compileflow.engine.core.definition.NodeContainer;
import com.alibaba.compileflow.engine.core.definition.var.HasVar;
import com.alibaba.compileflow.engine.core.definition.var.IVar;

import java.util.ArrayList;
import java.util.List;

/**
 * @author wuxiang
 * @author yusu
 */
public class BpmNode implements NodeContainer<FlowNode>, HasVar, Element {

    private final List<IVar> vars = new ArrayList<>();
    private final List<FlowNode> allNodes = new ArrayList<>();
    private String id;
    private String code;
    private String name;
    private String type;
    private String description;

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

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
    public String getTag() {
        return "UNDEFINED";
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public List<IVar> getVars() {
        return vars;
    }

    @Override
    public void addVar(IVar var) {
        vars.add(var);
    }

    @Override
    public List<FlowNode> getAllNodes() {
        return allNodes;
    }

    @Override
    public void addNode(FlowNode node) {
        allNodes.add(node);
    }

}
