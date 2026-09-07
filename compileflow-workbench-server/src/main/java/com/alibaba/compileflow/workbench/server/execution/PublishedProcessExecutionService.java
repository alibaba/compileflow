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
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.core.routing.AliasSelection;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.runtime.version.VersionRuntimeLease;
import com.alibaba.compileflow.deploy.runtime.version.VersionRuntimeManager;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.core.routing.AliasAdmission;
import com.alibaba.compileflow.engine.core.routing.AliasSelectionExecutor;
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
    private final ProcessEngine engine;
    private final AliasAdmission aliasAdmission;
    private final AliasSelectionExecutor aliasExecutor;
    private final VersionRuntimeManager versionRuntimeManager;

    @Autowired
    public PublishedProcessExecutionService(ProcessEngine engine, AliasAdmission aliasAdmission,
            ObjectProvider<VersionRuntimeManager> versionRuntimeManagerProvider) {
        this(engine, aliasAdmission, versionRuntimeManagerProvider.getIfAvailable());
    }

    private PublishedProcessExecutionService(ProcessEngine engine, AliasAdmission aliasAdmission,
            VersionRuntimeManager versionRuntimeManager) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.aliasAdmission = Objects.requireNonNull(aliasAdmission, "aliasAdmission");
        if (!(engine instanceof AliasSelectionExecutor executor)) {
            throw new IllegalArgumentException("Published execution requires preselected Alias execution support");
        }
        this.aliasExecutor = executor;
        this.versionRuntimeManager = versionRuntimeManager;
    }

    private static ProcessExecutionOptions engineOptions(ProcessRef selector, ProcessExecutionOptions options) {
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(options, "options");
        String invocationId =
                StringUtils.isNotBlank(options.getInvocationId())
                ? options.getInvocationId()
                : "inv-" + UUID.randomUUID();
        ProcessExecutionOptions.Builder builder = ProcessExecutionOptions.builder().invocationId(invocationId);
        if (selector instanceof ProcessRef.Alias) {
            AliasRoutingOptions routing = options.getAliasRouting();
            builder.aliasRouting(
                    routing.routingKey() == null ? new AliasRoutingOptions(invocationId, routing.attributes()) : routing);
        }
        return builder.build();
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

    ProcessExecutionResponse executeInstalled(ProcessRef.Version selector, VersionRuntimeLease installation,
            Map<String, Object> params, ProcessExecutionOptions options) {
        return execute(Objects.requireNonNull(selector, "selector"), null,
                Objects.requireNonNull(installation, "installation"), params, options);
    }

    private ProcessExecutionResponse execute(ProcessRef selector, AliasPin aliasPin, VersionRuntimeLease installation,
            Map<String, Object> params, ProcessExecutionOptions options) {
        long startedAtNanos = System.nanoTime();
        ProcessExecutionOptions engineOptions = engineOptions(selector, options);
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

    private EngineExecution executeEngine(ProcessRef selector, AliasPin aliasPin, VersionRuntimeLease installation,
            Map<String, Object> context, ProcessExecutionOptions options) {
        if (aliasPin == null) {
            if (selector instanceof ProcessRef.Alias alias) {
                for (int attempt = 0; ; attempt++) {
                    AliasSelection selection = aliasAdmission.admit(alias, options.getAliasRouting());
                    ProcessResult<Map<String, Object>> result =
                            aliasExecutor.execute(alias, selection, context, options);
                    if (result.isSuccess() || attempt == 1
                            || !ErrorCode.CF_EXEC_012.getCode().equals(result.getError().getCode())) {
                        return new EngineExecution(result, selection);
                    }
                }
            }
            return new EngineExecution(installation == null
                    ? executeExact(selector, context, options)
                    : executeInstalledExact((ProcessRef.Version) selector, installation, context, options), null);
        }
        ProcessRef.Alias alias = aliasPin.alias();
        AliasSelection selection = new AliasSelection(ProcessRef.version(alias.namespace(), alias.code(),
                        aliasPin.version()), aliasPin.target(), aliasPin.routeRevision());
        return new EngineExecution(aliasExecutor.execute(alias, selection, context, options), selection);
    }

    private ProcessResult<Map<String, Object>> executeExact(ProcessRef selector, Map<String, Object> context,
            ProcessExecutionOptions options) {
        if (!(selector instanceof ProcessRef.Version version) || versionRuntimeManager == null) {
            return engine.execute(selector, context, options);
        }
        VersionRuntimeLease lease;
        try {
            lease = versionRuntimeManager.acquireInstallation(version).join();
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
            VersionRuntimeLease installation, Map<String, Object> context, ProcessExecutionOptions options) {
        Objects.requireNonNull(installation, "installation");
        return engine.execute(version, context, options);
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
        AliasSelection selection = aliasAdmission.admit(ref, Objects.requireNonNull(routing, "routing"));
        return new AliasPin(ref, selection.version().version(), selection.aliasRevision(), selection.target());
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

    public static final class InvalidExecutionRequestException extends IllegalArgumentException {
        @Serial
        private static final long serialVersionUID = 1L;

        public InvalidExecutionRequestException(String message, IllegalArgumentException cause) {
            super(message, cause);
        }
    }
}
