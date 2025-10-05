package com.alibaba.compileflow.engine.core;

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.ErrorCode;
import com.alibaba.compileflow.engine.core.builder.generator.script.ScriptExecutorProvider;
import com.alibaba.compileflow.engine.core.builder.generator.script.impl.GroovyExecutor;
import com.alibaba.compileflow.engine.core.builder.generator.script.impl.MvelExecutor;
import com.alibaba.compileflow.engine.core.builder.generator.script.impl.QLExecutor;
import com.alibaba.compileflow.engine.core.extension.ExtensionManager;
import com.alibaba.compileflow.engine.core.infrastructure.bean.BeanProvider;
import com.alibaba.compileflow.engine.core.infrastructure.bean.SpringApplicationContextProvider;
import com.alibaba.compileflow.engine.core.infrastructure.bean.SpringBeanHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

/**
 * Handles one-time global initialization for the entire engine core.
 * This is triggered automatically before any engine instance is used.
 *
 * @author yusu
 */
public final class EngineInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(EngineInitializer.class);
    private static final boolean INITIALIZED;

    static {
        LOGGER.info("CompileFlow Core Initializing...");
        initScriptExecutors();
        initBeanProvider();
        ExtensionManager.getInstance().init();
        LOGGER.info("CompileFlow Core Initialization Complete.");
        INITIALIZED = true;
    }

    private EngineInitializer() {

    }

    /**
     * An empty method that, when called, ensures the static initializer block of this class
     * has been executed. This is a standard pattern to trigger class loading and static initialization.
     */
    public static void ensureInitialized() {
    }

    /**
     * Checks if the core components have been initialized.
     *
     * @return true if initialization is complete, false otherwise.
     */
    public static boolean isInitialized() {
        return INITIALIZED;
    }

    private static void initScriptExecutors() {
        ScriptExecutorProvider provider = ScriptExecutorProvider.getInstance();
        provider.registerScriptExecutor(new QLExecutor());
        provider.registerScriptExecutor(new MvelExecutor());
        provider.registerScriptExecutor(new GroovyExecutor());
        LOGGER.debug("Script executors (QLExecutor, MvelExecutor, GroovyExecutor) registered.");
    }

    /**
     * Initialize bean provider.
     */
    private static void initBeanProvider() {
        try {
            Class.forName("org.springframework.context.ApplicationContext");
            ApplicationContext context = SpringApplicationContextProvider.applicationContext;
            if (context != null) {
                BeanProvider.registerBeanHolder(SpringBeanHolder.of(context));
                LOGGER.debug("Spring detected. SpringBeanHolder registered successfully.");
            } else {
                throw new CompileFlowException.ConfigurationException(
                        ErrorCode.CF_CONFIG_001,
                        "Spring framework detected, but the ApplicationContext is null. " +
                                "Please ensure SpringApplicationContextProvider is properly initialized with your application's context. " +
                                "This can typically be achieved by defining it as a bean in your Spring configuration.",
                        null
                );
            }
        } catch (ClassNotFoundException e) {
            LOGGER.debug("Spring not detected in classpath. SpringBeanHolder will not be registered.");
        } catch (Exception e) {
            if (e instanceof CompileFlowException) {
                throw (CompileFlowException) e;
            }
            throw new CompileFlowException.SystemException(
                    ErrorCode.CF_CONFIG_001,
                    "An unexpected error occurred while initializing SpringBeanHolder.",
                    e
            );
        }
    }

}
