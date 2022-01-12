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
package com.alibaba.compileflow.engine.definition.common.var.impl;

import com.alibaba.compileflow.engine.common.util.DataType;
import com.alibaba.compileflow.engine.definition.common.AbstractElement;
import com.alibaba.compileflow.engine.definition.common.var.IVar;

/**
 * @author wuxiang
 * @author yusu
 */
public class Var extends AbstractElement implements IVar {

    private String name;

    private String description;

    private String dataType;

    private String contextVarName;

    private String defaultValue;

    private String inOutType;

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String getDataType() {
        return DataType.getPrimitiveClass(dataType);
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    @Override
    public String getContextVarName() {
        return contextVarName;
    }

    public void setContextVarName(String contextVarName) {
        this.contextVarName = contextVarName;
    }

    @Override
    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Override
    public String getInOutType() {
        return inOutType;
    }

    public void setInOutType(String inOutType) {
        this.inOutType = inOutType;
    }

}
