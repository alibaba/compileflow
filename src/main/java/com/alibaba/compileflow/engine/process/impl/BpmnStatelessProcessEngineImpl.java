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
package com.alibaba.compileflow.engine.process.impl;

import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.common.constants.FlowModelType;
import com.alibaba.compileflow.engine.definition.bpmn.BpmnModel;
import com.alibaba.compileflow.engine.process.preruntime.converter.FlowModelConverter;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.BpmnModelConverter;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;
import com.alibaba.compileflow.engine.runtime.impl.BpmnStatelessProcessRuntime;

import java.util.Map;

/**
 * @author yusu
 */
public class BpmnStatelessProcessEngineImpl extends AbstractProcessEngine<BpmnModel>
    implements ProcessEngine<BpmnModel> {

    @Override
    public Map<String, Object> execute(String code, Map<String, Object> context) {
        BpmnStatelessProcessRuntime runtime = getProcessRuntime(code);
        return runtime.start(context);
    }

    @Override
    public Map<String, Object> start(String code, Map<String, Object> context) {
        return execute(code, context);
    }

    @Override
    protected FlowModelType getFlowModelType() {
        return FlowModelType.BPMN;
    }

    @Override
    protected FlowModelConverter getFlowModelConverter() {
        return BpmnModelConverter.getInstance();
    }

    @Override
    protected AbstractProcessRuntime getRuntimeFromModel(BpmnModel bpmnModel) {
        return BpmnStatelessProcessRuntime.of(bpmnModel);
    }

}