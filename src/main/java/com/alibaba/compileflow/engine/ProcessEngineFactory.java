/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine;

import com.alibaba.compileflow.engine.common.constants.FlowModelType;
import com.alibaba.compileflow.engine.extension.ExtensionManager;
import com.alibaba.compileflow.engine.extension.PluginManager;
import com.alibaba.compileflow.engine.process.builder.generator.bean.BeanProvider;
import com.alibaba.compileflow.engine.process.builder.generator.bean.SpringApplicationContextProvider;
import com.alibaba.compileflow.engine.process.builder.generator.bean.SpringBeanHolder;
import com.alibaba.compileflow.engine.process.builder.generator.script.ScriptExecutorProvider;
import com.alibaba.compileflow.engine.process.builder.generator.script.impl.GroovyExecutor;
import com.alibaba.compileflow.engine.process.builder.generator.script.impl.MvelExecutor;
import com.alibaba.compileflow.engine.process.builder.generator.script.impl.QLExecutor;
import com.alibaba.compileflow.engine.process.impl.BpmnProcessEngineImpl;
import com.alibaba.compileflow.engine.process.impl.TbbpmProcessEngineImpl;
import org.springframework.context.ApplicationContext;

/**
 * Factory to get process engine instance.
 * Also handles one-time global initialization for the entire engine.
 *
 * @author wuxiang
 * @author yusu
 */
public class ProcessEngineFactory {

    private static final ProcessEngine TBBPM_PROCESS_ENGINE = new TbbpmProcessEngineImpl();
    private static final ProcessEngine BPMN_PROCESS_ENGINE = new BpmnProcessEngineImpl();

    // Static initializer block for one-time global setup.
    // This runs exactly once when the class is first loaded.
    static {
        // Initialize and register all available script executors.
        initScriptExecutors();
        // Initialize Spring bean provider if Spring ApplicationContext is available.
        initBeanProvider();
        PluginManager.getInstance().init();
        ExtensionManager.getInstance().init();
    }

    /**
     * Returns the default process engine (TBBPM).
     *
     * @return The process engine instance.
     */
    public static ProcessEngine getProcessEngine() {
        return TBBPM_PROCESS_ENGINE;
    }

    /**
     * Returns a process engine based on the specified flow model type.
     *
     * @param flowModelType The type of the flow model (BPMN or TBBPM).
     * @return The corresponding process engine instance.
     */
    public static ProcessEngine getProcessEngine(FlowModelType flowModelType) {
        if (flowModelType == null) {
            return TBBPM_PROCESS_ENGINE;
        }
        return flowModelType.equals(FlowModelType.BPMN) ? BPMN_PROCESS_ENGINE : TBBPM_PROCESS_ENGINE;
    }

    private static void initScriptExecutors() {
        ScriptExecutorProvider provider = ScriptExecutorProvider.getInstance();
        provider.registerScriptExecutor(new QLExecutor());
        provider.registerScriptExecutor(new MvelExecutor());
        provider.registerScriptExecutor(new GroovyExecutor());
    }

    private static void initBeanProvider() {
        // This check ensures that the engine doesn't have a hard dependency on Spring.
        // It will only enable Spring integration if it's actually present in the classpath
        // and an ApplicationContext is available.
        try {
            ApplicationContext context = SpringApplicationContextProvider.applicationContext;
            if (context != null) {
                BeanProvider.registerBeanHolder(SpringBeanHolder.of(context));
            }
        } catch (NoClassDefFoundError e) {
            // This catch block makes the Spring dependency optional.
            // If Spring classes are not found, we simply skip this initialization.
            System.out.println("Spring not detected, SpringBeanHolder will not be registered.");
        }
    }

}
