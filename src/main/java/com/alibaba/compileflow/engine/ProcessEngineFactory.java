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

import com.alibaba.compileflow.engine.common.constant.FlowModelType;
import com.alibaba.compileflow.engine.common.extension.ExtensionManager;
import com.alibaba.compileflow.engine.common.extension.PluginManager;
import com.alibaba.compileflow.engine.process.impl.BpmnProcessEngineImpl;
import com.alibaba.compileflow.engine.process.impl.TbbpmProcessEngineImpl;

/**
 * @author wuxiang
 * @author yusu
 */
public class ProcessEngineFactory {

    private static final ProcessEngine TBBPM_PROCESS_ENGINE = new TbbpmProcessEngineImpl();
    private static final ProcessEngine BPMN_PROCESS_ENGINE = new BpmnProcessEngineImpl();
    private static volatile boolean initialized = false;

    public static ProcessEngine getProcessEngine() {
        init();
        return TBBPM_PROCESS_ENGINE;
    }

    public static ProcessEngine getProcessEngine(FlowModelType flowModelType) {
        init();
        return flowModelType.equals(FlowModelType.BPMN) ? BPMN_PROCESS_ENGINE : TBBPM_PROCESS_ENGINE;
    }

    private static void init() {
        if (!initialized) {
            synchronized (ProcessEngineFactory.class) {
                if (!initialized) {
                    PluginManager.getInstance().init();
                    ExtensionManager.getInstance().init();
                    initialized = true;
                }
            }
        }
    }

}