package com.alibaba.compileflow.engine.core;

import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineProvider;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for ProcessEngine providers.
 * Provides unified configuration logic based on ProcessEngineConfig.
 * <p>
 * <b>PERFORMANCE NOTICE:</b> Each ProcessEngine created by this provider consumes
 * significant system resources (16-64 threads, several MB memory). The creation process
 * typically takes 100-500ms and should be treated as an expensive operation.
 * <p>
 * <b>Best Practice:</b> Create ProcessEngine instances sparingly and reuse them throughout
 * the application lifecycle. Prefer singleton patterns over repeated creation.
 *
 * @author yusu
 */
public abstract class AbstractProcessEngineProvider implements ProcessEngineProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractProcessEngineProvider.class);

    @Override
    public ProcessEngine createEngine(ProcessEngineConfig config) {
        EngineInitializer.ensureInitialized();

        // Create engine - this is an expensive operation!
        AbstractProcessEngine engine = doCreateEngine(config);

        LOGGER.info("ProcessEngine created: type={}, parallelCompilation={}, executors={} " +
                        "[Resource-intensive operation: {} threads created]",
                config.getModelType(),
                config.isParallelCompilationEnabled(),
                config.getExecutorConfig(),
                config.getExecutorConfig().getCompilationThreads() +
                        config.getExecutorConfig().getExecutionThreads() +
                        config.getExecutorConfig().getEventThreads() +
                        config.getExecutorConfig().getScheduleThreads());

        return engine;
    }

    protected abstract AbstractProcessEngine doCreateEngine(ProcessEngineConfig config);

}
