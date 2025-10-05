/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.core;

import com.alibaba.compileflow.engine.*;
import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.ErrorCode;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.config.ProcessPropertyProvider;
import com.alibaba.compileflow.engine.core.builder.ProcessRuntimeBuilder;
import com.alibaba.compileflow.engine.core.builder.converter.FlowModelConverter;
import com.alibaba.compileflow.engine.core.builder.generator.ProcessCodeGenerator;
import com.alibaba.compileflow.engine.core.classloader.ProcessClassLoaderManager;
import com.alibaba.compileflow.engine.core.classloader.ProcessClassLoaderResolver;
import com.alibaba.compileflow.engine.core.definition.FlowModel;
import com.alibaba.compileflow.engine.core.definition.Node;
import com.alibaba.compileflow.engine.core.definition.StatefulElement;
import com.alibaba.compileflow.engine.core.event.ProcessEventPublisher;
import com.alibaba.compileflow.engine.core.executor.ProcessEngineExecutors;
import com.alibaba.compileflow.engine.core.infrastructure.ExceptionClassifier;
import com.alibaba.compileflow.engine.core.infrastructure.LogContext;
import com.alibaba.compileflow.engine.core.infrastructure.utils.ObjectMapperUtils;
import com.alibaba.compileflow.engine.core.observability.tracing.TracingHelper;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntime;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeWrapper;
import com.alibaba.compileflow.engine.core.runtime.RuntimeSpec;
import com.alibaba.compileflow.engine.core.runtime.cache.DefaultProcessRuntimeCache;
import com.alibaba.compileflow.engine.core.runtime.cache.ProcessRuntimeCache;
import com.alibaba.compileflow.engine.core.runtime.compilation.CompilationService;
import com.alibaba.compileflow.engine.core.runtime.compilation.DefaultCompilationService;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContext;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContextHolder;
import com.alibaba.compileflow.engine.core.runtime.executor.DynamicClassExecutor;
import com.alibaba.compileflow.engine.core.runtime.executor.ProcessExecutor;
import com.alibaba.compileflow.engine.core.runtime.instance.StatefulProcessInstance;
import com.alibaba.compileflow.engine.core.runtime.provider.DefaultProcessRuntimeProvider;
import com.alibaba.compileflow.engine.core.runtime.provider.ProcessRuntimeProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Loads, compiles, caches and executes flows.
 * Uses single-flight per digest and SWR; CAS prevents stale installs.
 *
 * @author yusu
 */
public abstract class AbstractProcessEngine<T extends FlowModel>
        implements ProcessEngine<T>, ProcessAdminService, ProcessToolingService<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractProcessEngine.class);
    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperUtils.jacksonMapper();

    private static final ProcessExecutor PROCESS_EXECUTOR = new ProcessExecutor();

    private final ProcessEngineExecutors executors;
    private final DynamicClassExecutor dynamicClassExecutor;
    private final ProcessClassLoaderManager classLoaderManager;
    private final ProcessEventPublisher eventPublisher = new ProcessEventPublisher();

    /**
     * Cache of compiled runtimes keyed by process code.
     */
    private final ProcessRuntimeCache runtimeCache;

    /**
     * Enable parallel pre-compilation for batch deploy.
     */
    private final boolean parallelCompilationEnabled;

    /**
     * Optional default ClassLoader; resolved context-aware if null.
     */
    private final ClassLoader classLoader;

    private final CompilationService compilationService;
    private final ProcessRuntimeProvider processRuntimeProvider;

    protected AbstractProcessEngine(ProcessEngineConfig config) {
        this.parallelCompilationEnabled = config.isParallelCompilationEnabled();
        this.classLoader = config.getClassLoader();
        this.executors = ProcessEngineExecutors.create(config.getExecutorConfig());
        this.classLoaderManager = new ProcessClassLoaderManager(true, executors.schedule(),
                ProcessPropertyProvider.ClassLoader.getMaintenanceIntervalMinutes());
        this.dynamicClassExecutor = new DynamicClassExecutor(classLoaderManager, executors);

        this.runtimeCache = new DefaultProcessRuntimeCache(classLoaderManager, executors.schedule());

        this.compilationService = new DefaultCompilationService(
                runtimeCache,
                (source, cl, digest) -> createRuntime(source, cl, digest),
                executors,
                eventPublisher
        );
        this.processRuntimeProvider = new DefaultProcessRuntimeProvider(runtimeCache, compilationService);
    }

    public ProcessEngineExecutors getExecutors() {
        return executors;
    }

    // ---------------------------------------------------------------------------------------------
    // Public API: execute / trigger
    // ---------------------------------------------------------------------------------------------

    @Override
    public <I, O> ProcessResult<O> execute(ProcessSource source, I input, Class<O> outputType) {
        try {
            Map<String, Object> ctx = convertToContext(input);
            ProcessResult<Map<String, Object>> r = execute(source, ctx);
            return convertResult(r, outputType);
        } catch (Exception e) {
            LOGGER.error("Process execution failed unexpectedly: code={}", source.getCode(), e);
            return ProcessResult.failure("Process execute failed: " + e.getMessage());
        }
    }

    @Override
    public ProcessResult<Map<String, Object>> execute(ProcessSource source, Map<String, Object> context) {
        String processCode = source.getCode();
        EngineExecutionContext execContext = setupExecutionContext(processCode, context);

        try {
            eventPublisher.publishExecutionStarted(processCode);

            ProcessRuntime runtime = getProcessRuntime(source);
            Map<String, Object> result = PROCESS_EXECUTOR.execute(
                    processCode, runtime.getProcessInstanceClass(),
                    context == null ? new HashMap<>() : context);

            long durationMs = execContext.duration();
            eventPublisher.publishExecutionCompleted(processCode, durationMs);
            return ProcessResult.success(result, execContext.traceId());
        } catch (Exception e) {
            long durationMs = execContext.duration();
            CompileFlowException cfException = ExceptionClassifier.classify(e)
                    .withContext("processCode", processCode)
                    .withContext("durationMs", durationMs)
                    .withContext("traceId", execContext.traceId());
            LOGGER.error("Process execution failed: code={}, durationMs={}, errorCode={}",
                    processCode, durationMs, cfException.getErrorCode().getCode(), cfException);
            eventPublisher.publishExecutionFailed(processCode, durationMs, cfException.getMessage());
            return ProcessResult.failure(cfException.getDetailedMessage(), execContext.traceId());
        } finally {
            cleanupExecutionContext();
        }
    }

    @Override
    public <E, O> ProcessResult<O> trigger(ProcessSource source, String tag, String event, E payload, Class<O> outputType) {
        try {
            Map<String, Object> ctx = convertToContext(payload);
            ProcessResult<Map<String, Object>> result = trigger(source, tag, event, ctx);
            return convertResult(result, outputType);
        } catch (Exception e) {
            LOGGER.error("Process trigger failed unexpectedly: code={}, tag={}, event={}",
                    source.getCode(), tag, event, e);
            return ProcessResult.failure("Process trigger failed: " + e.getMessage());
        }
    }

    @Override
    public ProcessResult<Map<String, Object>> trigger(ProcessSource source, String tag, String event, Map<String, Object> context) {
        String processCode = source.getCode();
        EngineExecutionContext execContext = setupExecutionContext(processCode, context);

        try {
            eventPublisher.publishTriggerStarted(processCode, tag, event);

            ProcessRuntime runtime = getProcessRuntime(source);
            validateStatefulProcess(source, tag, runtime);
            Class<?> clazz = runtime.getProcessInstanceClass();
            if (!StatefulProcessInstance.class.isAssignableFrom(clazz)) {
                throw new CompileFlowException.BusinessException(ErrorCode.CF_VALIDATION_003,
                        "Process '" + processCode + "' is stateless; trigger() requires a stateful process.");
            }
            Class<? extends StatefulProcessInstance> sClazz = (Class<? extends StatefulProcessInstance>) clazz;
            Map<String, Object> result = PROCESS_EXECUTOR.trigger(processCode, sClazz, tag, event,
                    context == null ? new HashMap<>() : context);

            long durationMs = execContext.duration();
            eventPublisher.publishTriggerCompleted(processCode, tag, event, durationMs);
            return ProcessResult.success(result, execContext.traceId());
        } catch (Exception e) {
            long durationMs = execContext.duration();
            CompileFlowException cfException = ExceptionClassifier.classify(e)
                    .withContext("processCode", processCode)
                    .withContext("durationMs", durationMs)
                    .withContext("traceId", execContext.traceId())
                    .withContext("tag", tag)
                    .withContext("event", event);
            LOGGER.error("Process trigger failed: code={}, tag={}, event={}, durationMs={}, errorCode={}",
                    processCode, tag, event, durationMs, cfException.getErrorCode().getCode(), cfException);
            eventPublisher.publishTriggerFailed(processCode, tag, event, durationMs, cfException.getMessage());
            return ProcessResult.failure(cfException.getDetailedMessage(), execContext.traceId());
        } finally {
            cleanupExecutionContext();
        }
    }

    @Override
    public <E, O> ProcessResult<O> trigger(ProcessSource processSource, String tag, E payload, Class<O> outputType) {
        return trigger(processSource, tag, null, payload, outputType);
    }

    @Override
    public ProcessResult<Map<String, Object>> trigger(ProcessSource processSource, String tag, Map<String, Object> context) {
        return trigger(processSource, tag, null, context);
    }

    // ---------------------------------------------------------------------------------------------
    // Admin / tooling entry points
    // ---------------------------------------------------------------------------------------------

    @Override
    public ProcessAdminService admin() {
        return this;
    }

    @Override
    public ProcessToolingService<T> tooling() {
        return this;
    }

    // ---------------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------------

    /**
     * Shutdown executors, classloaders and caches; blocks until termination.
     */
    @Override
    public void close() {
        LOGGER.info("Shutting down ProcessEngine...");

        long start = System.currentTimeMillis();
        int cacheSize = (int) runtimeCache.size();

        try {
            runtimeCache.invalidateAll();
            compilationService.close();

            dynamicClassExecutor.close();
            classLoaderManager.close();
            executors.close();

            LOGGER.info("ProcessEngine shutdown completed in {}ms (cached={})",
                    (System.currentTimeMillis() - start), cacheSize);
        } catch (Exception e) {
            LOGGER.error("ProcessEngine shutdown failed after {}ms",
                    (System.currentTimeMillis() - start), e);
            throw new RuntimeException("ProcessEngine shutdown failed", e);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Admin APIs
    // ---------------------------------------------------------------------------------------------

    @Override
    public void deploy(ProcessSource... sources) {
        deploy(null, sources);
    }

    @Override
    public void deploy(ClassLoader loader, ProcessSource... sources) {
        LOGGER.info("Deploying {} processes: codes={}", sources.length,
                Arrays.stream(sources).map(ProcessSource::getCode).collect(Collectors.joining(", ")));
        long startTime = System.currentTimeMillis();
        compileProcesses(loader, sources);
        LOGGER.info("Deployed {} processes in {}ms", sources.length, (System.currentTimeMillis() - startTime));
    }

    private void compileProcesses(ClassLoader loader, ProcessSource... sources) {
        if (ArrayUtils.isEmpty(sources)) {
            return;
        }

        String[] codes = Arrays.stream(sources).map(ProcessSource::getCode).toArray(String[]::new);
        long startTime = System.currentTimeMillis();

        ClassLoader cl = resolveEffectiveClassLoader(loader);
        try {
            if (this.parallelCompilationEnabled && sources.length > 1) {
                compilationService.compileBatch(cl, sources);
            } else {
                for (ProcessSource src : sources) {
                    Objects.requireNonNull(src.getCode(), "Process code must not be null");
                    ProcessRuntimeWrapper wrapper = compilationService.compileSync(src, cl);
                    String wanted = RuntimeSpec.of(src, cl).getDigest();
                    if (!Objects.equals(wrapper.getDigest(), wanted)) {
                        LOGGER.warn("Stale process runtime used (SWR): code={}, wantedDigest={}, currentDigest={}",
                                src.getCode(), wanted, wrapper.getDigest());
                    } else {
                        LOGGER.debug("deploy-success code={} digest={}", src.getCode(), wrapper.getDigest());
                    }
                }
            }
            long durationMs = System.currentTimeMillis() - startTime;
            eventPublisher.publishCompilationCompleted(codes, durationMs);
        } catch (Exception e) {
            LOGGER.error("Process deployment failed: codes={}",
                    Arrays.toString(codes), e);
            throw e;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Abstract hooks
    // ---------------------------------------------------------------------------------------------

    protected abstract ProcessRuntimeBuilder getProcessRuntimeBuilder();

    public abstract FlowModelConverter<T> getFlowModelConverter();

    public abstract ProcessCodeGenerator getProcessCodeGenerator(T flowModel);

    // ---------------------------------------------------------------------------------------------
    // Model & Code generation
    // ---------------------------------------------------------------------------------------------

    /**
     * Convert source to in-memory {@link FlowModel} via the configured converter.
     */
    @Override
    public T loadFlowModel(ProcessSource processSource) {
        try {
            return getFlowModelConverter().convertToModel(processSource);
        } catch (Exception e) {
            LOGGER.error("Failed to load flow model: code={}",
                    processSource.getCode(), e);
            throw new CompileFlowException.SystemException(
                    ErrorCode.CF_RESOURCE_002, "Failed to load flow model", e);
        }
    }

    /**
     * Generate Java code from the converted {@link FlowModel}.
     */
    @Override
    public String generateJavaCode(ProcessSource processSource) {
        try {
            T model = getFlowModelConverter().convertToModel(processSource);
            return getProcessCodeGenerator(model).generateCode();
        } catch (Exception e) {
            LOGGER.error("Failed to generate Java code: code={}",
                    processSource.getCode(), e);
            throw new CompileFlowException.SystemException(
                    ErrorCode.CF_COMPILE_002, "Failed to generate Java code", e);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Runtime acquisition
    // ---------------------------------------------------------------------------------------------

    private ProcessRuntime getProcessRuntime(ProcessSource processSource) {
        ClassLoader cl = resolveEffectiveClassLoader(this.classLoader);
        return processRuntimeProvider.getRuntime(cl, processSource);
    }

    /**
     * Ensure the process is stateful and the tagged node exists.
     */
    private void validateStatefulProcess(ProcessSource source, String tag, ProcessRuntime runtime) {
        FlowModel flowModel = runtime.getFlowModel();
        if (!flowModel.isStateful()) {
            throw new CompileFlowException.BusinessException(ErrorCode.CF_EXEC_008,
                    "Process '" + source.getCode() + "' is not stateful.");
        }
        Node node = flowModel.getNodeByTag(tag);
        if (!(node instanceof StatefulElement)) {
            throw new CompileFlowException.BusinessException(ErrorCode.CF_EXEC_008,
                    "Process '" + source.getCode() + "' does not have a stateful node with tag '" + tag + "'.");
        }
    }

    private ProcessRuntime createRuntime(ProcessSource processSource, ClassLoader cl, String digest) {
        ProcessRuntimeBuilder processRuntimeBuilder = getProcessRuntimeBuilder();
        ProcessRuntime runtime = processRuntimeBuilder.buildProcessRuntime(processSource, cl);
        classLoaderManager.registerClassLoader(
                digest,
                runtime.getProcessInstanceClass().getClassLoader(),
                digest
        );
        return runtime;
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /**
     * Map POJO input to context map (no-op if already a map).
     */
    @SuppressWarnings("unchecked")
    private <I> Map<String, Object> convertToContext(I input) {
        if (input == null || input instanceof Map) {
            return input == null ? new HashMap<>() : (Map<String, Object>) input;
        }
        return OBJECT_MAPPER.convertValue(input, new TypeReference<Map<String, Object>>() {
        });
    }

    /**
     * Map result context to the requested output type.
     */
    private <O> ProcessResult<O> convertResult(ProcessResult<Map<String, Object>> result, Class<O> outputType) {
        if (result.isSuccess()) {
            if (outputType == Map.class) {
                return ProcessResult.success((O) result.getData());
            }
            O out = OBJECT_MAPPER.convertValue(result.getData(), outputType);
            return ProcessResult.success(out);
        }
        return ProcessResult.failure(result.getErrorMessage());
    }

    private EngineExecutionContext createExecutionContext(String processCode, Map<String, Object> context) {
        return EngineExecutionContext.builder()
                .startTime(System.currentTimeMillis())
                .traceId(TracingHelper.getCurrentTraceId())
                .processCode(processCode)
                .processContext(context)
                .processEngine(this)
                .executors(executors)
                .dynamicClassExecutor(dynamicClassExecutor)
                .build();
    }

    /**
     * Create and bind execution context, initialize logging.
     */
    private EngineExecutionContext setupExecutionContext(String processCode, Map<String, Object> context) {
        EngineExecutionContext execContext = createExecutionContext(processCode, context);
        EngineExecutionContextHolder.set(execContext);
        LogContext.setTraceId(execContext.traceId());
        LogContext.setProcessCode(processCode);
        return execContext;
    }

    /**
     * Clear execution and logging context.
     */
    private void cleanupExecutionContext() {
        try {
            LogContext.clear();
            EngineExecutionContextHolder.clear();
        } catch (Exception cleanupEx) {
            LOGGER.warn("Failed to cleanup execution context", cleanupEx);
        }
    }

    private ClassLoader resolveEffectiveClassLoader(ClassLoader override) {
        ClassLoader provided = (override != null) ? override : this.classLoader;
        return ProcessClassLoaderResolver.resolveEffectiveClassLoader(provided, AbstractProcessEngine.class);
    }

}
