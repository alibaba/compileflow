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
package com.alibaba.compileflow.engine;

import com.alibaba.compileflow.engine.common.constants.FlowModelType;
import com.alibaba.compileflow.engine.process.impl.BpmnStatelessProcessEngineImpl;
import com.alibaba.compileflow.engine.process.impl.TbbpmStatelessProcessEngineImpl;

import java.util.HashMap;
import java.util.Map;

/**
 * @author wuxiang
 * @author yusu
 */
public class ProcessEngineFactory {

    private static final Map<FlowModelType, ProcessEngine> STATELESS_PROCESS_ENGINES = new HashMap<>();

    public static ProcessEngine getProcessEngine() {
        return getStatelessProcessEngine(FlowModelType.TBBPM);
    }

    public static ProcessEngine getStatelessProcessEngine(FlowModelType flowModelType) {

        if (STATELESS_PROCESS_ENGINES.get(flowModelType) == null) {
            synchronized (STATELESS_PROCESS_ENGINES) {
                if (STATELESS_PROCESS_ENGINES.get(flowModelType) == null) {
                    if (FlowModelType.BPMN.equals(flowModelType)) {
                        STATELESS_PROCESS_ENGINES.put(flowModelType, new BpmnStatelessProcessEngineImpl());
                    } else {
                        STATELESS_PROCESS_ENGINES.put(flowModelType, new TbbpmStatelessProcessEngineImpl());
                    }
                }
            }
        }
        return STATELESS_PROCESS_ENGINES.get(flowModelType);
    }

}