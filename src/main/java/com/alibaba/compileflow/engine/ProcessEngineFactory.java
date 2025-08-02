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

import com.alibaba.compileflow.engine.common.CompileFlowException;
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
import com.alibaba.compileflow.engine.process.impl.AbstractProcessEngine;
import com.alibaba.compileflow.engine.process.impl.BpmnProcessEngineImpl;
import com.alibaba.compileflow.engine.process.impl.TbbpmProcessEngineImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.concurrent.ExecutorService;

/**
 * The main entry point for creating {@link ProcessEngine} instances.
 * This factory provides a builder for custom configurations and convenient shortcut methods
 * for common use cases.
 *
 * @author wuxiang
 * @author yusu
 */
public final class ProcessEngineFactory {

    private static final Logger logger = LoggerFactory.getLogger(ProcessEngineFactory.class);

    private static final ProcessEngine TBBPM_PROCESS_ENGINE = new TbbpmProcessEngineImpl();
    private static final ProcessEngine BPMN_PROCESS_ENGINE = new BpmnProcessEngineImpl();

    static {
        logger.info("ProcessEngineFactory class loading and initializing...");
        initScriptExecutors();
        initBeanProvider();
        PluginManager.getInstance().init();
        ExtensionManager.getInstance().init();
        logger.info("ProcessEngineFactory initialization complete.");
    }

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private ProcessEngineFactory() {
    }

    /**
     * Returns a new {@link ProcessEngineBuilder} to create and configure a ProcessEngine.
     *
     * @return a new ProcessEngineBuilder instance.
     */
    public static ProcessEngineBuilder builder() {
        return new ProcessEngineBuilder();
    }

    /**
     * [Shortcut Method] Creates a new ProcessEngine instance with default settings.
     */
    public static ProcessEngine newDefaultInstance() {
        return new ProcessEngineBuilder().build();
    }

    /**
     * [Shortcut Method] Creates a new ProcessEngine instance with concurrent compilation enabled.
     */
    public static ProcessEngine newInstanceWithConcurrentCompilation() {
        return new ProcessEngineBuilder().enableConcurrentCompilation(true).build();
    }

    /**
     * [Shortcut Method] Creates a new ProcessEngine instance of a specific type.
     */
    public static ProcessEngine newInstance(FlowModelType type) {
        return new ProcessEngineBuilder().flowModelType(type).build();
    }

    /**
     * @deprecated Use {@link #builder()} or shortcut methods like {@link #newDefaultInstance()}.
     */
    @Deprecated
    public static ProcessEngine getProcessEngine() {
        logger.warn("Usage of deprecated method getProcessEngine(). Please switch to modern API.");
        return TBBPM_PROCESS_ENGINE;
    }

    /**
     * @deprecated Use {@link #builder()} or shortcut methods like {@link #newInstance(FlowModelType)}.
     */
    @Deprecated
    public static ProcessEngine getProcessEngine(FlowModelType flowModelType) {
        logger.warn("Usage of deprecated method getProcessEngine(type). Please switch to modern API.");
        if (flowModelType == null || flowModelType.equals(FlowModelType.TBBPM)) {
            return TBBPM_PROCESS_ENGINE;
        }
        return BPMN_PROCESS_ENGINE;
    }

    private static void initScriptExecutors() {
        ScriptExecutorProvider provider = ScriptExecutorProvider.getInstance();
        provider.registerScriptExecutor(new QLExecutor());
        provider.registerScriptExecutor(new MvelExecutor());
        provider.registerScriptExecutor(new GroovyExecutor());
        logger.info("Script executors (QLExecutor, MvelExecutor, GroovyExecutor) registered.");
    }

    private static void initBeanProvider() {
        try {
            Class.forName("org.springframework.context.ApplicationContext");
            ApplicationContext context = SpringApplicationContextProvider.applicationContext;
            if (context != null) {
                BeanProvider.registerBeanHolder(SpringBeanHolder.of(context));
                logger.info("Spring detected. SpringBeanHolder registered successfully.");
            } else {
                throw new CompileFlowException(
                        "Spring framework detected, but the ApplicationContext is null. " +
                                "Please ensure SpringApplicationContextProvider is properly initialized with your application's context. " +
                                "This can typically be achieved by defining it as a bean in your Spring configuration.");
            }
        } catch (ClassNotFoundException e) {
            logger.info("Spring not detected in classpath. SpringBeanHolder will not be registered.");
        } catch (Exception e) {
            if (e instanceof CompileFlowException) {
                throw (CompileFlowException) e;
            }
            throw new CompileFlowException("An unexpected error occurred while initializing SpringBeanHolder.", e);
        }
    }

    /**
     * A builder for creating and configuring {@link ProcessEngine} instances.
     * It provides a fluent API to set various options before building the final engine object.
     */
    public static class ProcessEngineBuilder {
        private FlowModelType flowModelType = FlowModelType.TBBPM;
        private boolean concurrentCompilation = false;
        private ExecutorService compilationExecutorService;
        private ClassLoader classLoader;

        ProcessEngineBuilder() {}

        public ProcessEngineBuilder flowModelType(FlowModelType flowModelType) {
            this.flowModelType = (flowModelType != null) ? flowModelType : FlowModelType.TBBPM;
            return this;
        }

        public ProcessEngineBuilder enableConcurrentCompilation(boolean enabled) {
            this.concurrentCompilation = enabled;
            return this;
        }

        public ProcessEngineBuilder compilationExecutorService(ExecutorService compilationExecutorService) {
            this.compilationExecutorService = compilationExecutorService;
            if (this.compilationExecutorService != null) {
                this.concurrentCompilation = true;
            }
            return this;
        }

        public ProcessEngineBuilder classLoader(ClassLoader classLoader) {
            this.classLoader = classLoader;
            return this;
        }

        public ProcessEngine build() {
            AbstractProcessEngine engine;
            if (FlowModelType.BPMN.equals(this.flowModelType)) {
                engine = new BpmnProcessEngineImpl();
            } else {
                engine = new TbbpmProcessEngineImpl();
            }

            engine.setConcurrentCompilation(this.concurrentCompilation);
            engine.setCompilationExecutorService(this.compilationExecutorService);

            if (this.classLoader != null) {
                 engine.setClassLoader(this.classLoader);
            }

            return engine;
        }
    }

}
