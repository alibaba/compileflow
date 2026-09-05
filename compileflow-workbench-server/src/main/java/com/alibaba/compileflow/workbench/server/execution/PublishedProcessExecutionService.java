/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.workbench.server.execution;

import com.alibaba.compileflow.engine.ProcessAliasTarget;
import com.alibaba.compileflow.engine.AliasRoutingOptions;
import com.alibaba.compileflow.engine.ProcessExecution;
import com.alibaba.compileflow.engine.ProcessExecutionOptions;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.core.routing.AliasSelection;
import com.alibaba.compileflow.deploy.api.ProcessDeploymentService;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.api.version.PublishedProcessVersion;
import com.alibaba.compileflow.deploy.runtime.install.RuntimeInstallationLease;
import com.alibaba.compileflow.deploy.runtime.install.RuntimeInstaller;
import com.alibaba.compileflow.engine.spring.boot.autoconfigure.ProcessEngineRegistry;
import java.io.Serial;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Executes published Process versions and resolves authoritative Alias routes.
 *
 * @author yusu
 */
@Service
public class PublishedProcessExecutionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PublishedProcessExecutionService.class);
    private final ProcessEngineRegistry engineRegistry;
    private final ProcessDeploymentService deploymentService;
    private final RuntimeInstaller runtimeInstaller;

    @Autowired
    public PublishedProcessExecutionService(ProcessEngineRegistry engineRegistry,
            ProcessDeploymentService deploymentService, ObjectProvider<RuntimeInstaller> installerProvider) {
        this(engineRegistry, deploymentService, installerProvider.getIfAvailable());
    }

    private PublishedProcessExecutionService(ProcessEngineRegistry engineRegistry,
            ProcessDeploymentService deploymentService, RuntimeInstaller runtimeInstaller) {
        this.engineRegistry = engineRegistry;
        this.deploymentService = deploymentService;
        this.runtimeInstaller = runtimeInstaller;
    }

    private static ProcessExecutionOptions ensureInvocationId(ProcessExecutionOptions options) {
        Objects.requireNonNull(options, "options");
        if (StringUtils.isNotBlank(options.getInvocationId())) {
            return options;
        }
        return ProcessExecutionOptions
            .builder()
            .invocationId("inv-" + UUID.randomUUID())
            .aliasRouting(options.getAliasRouting())
            .build();
    }

    public ProcessExecutionResponse execute(ProcessRef selector, Map<String, Object> params,
            ProcessExecutionOptions options) {
        return execute(Objects.requireNonNull(selector, "selector"), null, null, params, options);
    }

    public ProcessExecutionResponse execute(AliasPin aliasPin, Map<String, Object> params,
            ProcessExecutionOptions options) {
        Objects.requireNonNull(aliasPin, "aliasPin");
        return execute(aliasPin.alias(), aliasPin, null, params, options);
    }

    ProcessExecutionResponse executeInstalled(ProcessRef.Version selector, RuntimeInstallationLease installation,
            Map<String, Object> params, ProcessExecutionOptions options) {
        return execute(Objects.requireNonNull(selector, "selector"), null,
                Objects.requireNonNull(installation, "installation"), params, options);
    }

    private ProcessExecutionResponse execute(ProcessRef selector, AliasPin aliasPin,
            RuntimeInstallationLease installation, Map<String, Object> params, ProcessExecutionOptions options) {
        long startedAtNanos = System.nanoTime();
        ProcessExecutionOptions engineOptions = ensureInvocationId(options);
        Map<String, Object> context = new HashMap<>(Objects.requireNonNull(params, "params"));
        EngineExecution engineExecution = executeEngine(selector, aliasPin, installation, context, engineOptions);
        ProcessResult<Map<String, Object>> result = engineExecution.result();
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);

        if (!result.isSuccess()) {
            ProcessExecution execution = result.getExecution();
            LOGGER.warn("Process execution failed: code={}, errorCode={}, invocationId={}", selector.code(),
                    result.getError().getCode(), execution.getInvocationId());
        }
        return ProcessExecutionResponse
            .from(result, durationMs)
            .withRouting(routingResponse(selector, result.getExecution(), engineExecution.aliasSelection()));
    }

    private EngineExecution executeEngine(ProcessRef selector, AliasPin aliasPin, RuntimeInstallationLease installation,
            Map<String, Object> context, ProcessExecutionOptions options) {
        if (aliasPin == null) {
            if (selector instanceof ProcessRef.Alias alias) {
                ProcessEngineRegistry.AliasExecution execution =
                        engineRegistry.executeAliasWithSelection(alias, this::requirePublishedModelType, context,
                                options);
                return new EngineExecution(execution.result(), execution.selection());
            }
            return new EngineExecution(installation == null
                    ? executeExact(selector, context, options)
                    : executeInstalledExact((ProcessRef.Version) selector, installation, context, options), null);
        }
        ProcessRef.Alias alias = aliasPin.alias();
        AliasSelection selection = new AliasSelection(ProcessRef.version(alias.namespace(), alias.code(),
                        aliasPin.version()), aliasPin.target(), aliasPin.routeRevision());
        return new EngineExecution(engineRegistry.executeAliasSelection(alias, selection,
                        this::requirePublishedModelType, context, options), selection);
    }

    private ProcessResult<Map<String, Object>> executeExact(ProcessRef selector, Map<String, Object> context,
            ProcessExecutionOptions options) {
        if (!(selector instanceof ProcessRef.Version version) || runtimeInstaller == null) {
            return engineRegistry.execute(selector, this::requirePublishedModelType, context, options);
        }
        RuntimeInstallationLease lease;
        try {
            lease = runtimeInstaller.acquireInstallation(version).join();
        } catch (CompletionException failure) {
            if (failure.getCause() instanceof DeploymentException deploymentFailure) {
                throw deploymentFailure;
            }
            throw failure;
        }
        try {
            return executeInstalledExact(version, lease, context, options);
        } finally {
            lease.close();
        }
    }

    private ProcessResult<Map<String, Object>> executeInstalledExact(ProcessRef.Version version,
            RuntimeInstallationLease installation, Map<String, Object> context, ProcessExecutionOptions options) {
        ProcessModelType modelType = installation
            .getModelType()
            .orElseThrow(() -> new IllegalStateException("Exact runtime lease has no model type"));
        return engineRegistry.get(modelType).execute(version, context, options);
    }

    private static ExecutionRoutingResponse routingResponse(ProcessRef selector, ProcessExecution execution,
            AliasSelection aliasSelection) {
        ProcessRef.Version processVersion = execution.getProcessVersion();
        String requestedVersion = selector instanceof ProcessRef.Version version ? version.version() : null;
        String requestedAlias = selector instanceof ProcessRef.Alias alias ? alias.alias() : null;
        String namespace =
                selector instanceof ProcessRef.Version version
                ? version.namespace()
                : ((ProcessRef.Alias) selector).namespace();
        return new ExecutionRoutingResponse(namespace, requestedVersion, requestedAlias,
                processVersion == null ? null : processVersion.version(), requestedAlias,
                aliasSelection == null ? null : aliasSelection.aliasRevision(),
                aliasSelection == null ? null : aliasSelection.target());
    }

    private record EngineExecution(ProcessResult<Map<String, Object>> result, AliasSelection aliasSelection) {
        private EngineExecution {
            result = Objects.requireNonNull(result, "result");
        }
    }

    public AliasPin resolveAliasPin(String code, String namespace, String alias, AliasRoutingOptions routing) {
        ProcessRef.Alias ref;
        try {
            ref = ProcessRef.alias(StringUtils.defaultIfBlank(namespace, ProcessRef.DEFAULT_NAMESPACE), code, alias);
        } catch (IllegalArgumentException exception) {
            throw new InvalidExecutionRequestException(exception.getMessage(), exception);
        }
        AliasSelection selection = engineRegistry.admitAlias(ref, Objects.requireNonNull(routing, "routing"));
        return new AliasPin(ref, selection.version().version(), selection.aliasRevision(), selection.target());
    }

    private ProcessModelType requirePublishedModelType(ProcessRef.Version ref) {
        if (runtimeInstaller != null) {
            ProcessModelType locallyInstalled = runtimeInstaller.findInstalledModelType(ref).orElse(null);
            if (locallyInstalled != null) {
                return locallyInstalled;
            }
        }
        return deploymentService
            .getVersion(ref)
            .map(PublishedProcessVersion::getModelType)
            .orElseThrow(() -> new PublishedProcessVersionNotFoundException(ref));
    }

    /**
     * Exact Alias selection persisted by the whole-invocation async queue.
     *
     * @param alias         originally requested published Alias
     * @param version       immutable version selected for every attempt
     * @param routeRevision authoritative Alias revision observed during selection
     * @param target        selected stable or candidate target
     */
    public record AliasPin(ProcessRef.Alias alias, String version, long routeRevision, ProcessAliasTarget target) {
        public AliasPin {
            alias = Objects.requireNonNull(alias, "alias");
            version = ProcessRef.version(alias.namespace(), alias.code(), version).version();
            if (routeRevision <= 0L) {
                throw new IllegalArgumentException("routeRevision must be greater than 0");
            }
            target = Objects.requireNonNull(target, "target");
        }
    }

    public static final class PublishedProcessVersionNotFoundException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public PublishedProcessVersionNotFoundException(ProcessRef.Version ref) {
            super("Published process version not found: " + ref.namespace() + "/" + ref.code() + "@" + ref.version());
        }
    }

    public static final class InvalidExecutionRequestException extends IllegalArgumentException {
        @Serial
        private static final long serialVersionUID = 1L;

        public InvalidExecutionRequestException(String message, IllegalArgumentException cause) {
            super(message, cause);
        }
    }
}
