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
package com.alibaba.compileflow.workbench.server.monitoring;

import com.alibaba.compileflow.engine.ProcessExecution;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.spi.event.ProcessEvent;
import com.alibaba.compileflow.engine.spi.event.ProcessEvent.ExecutionAttribution;
import com.alibaba.compileflow.engine.spi.event.ProcessEventListener;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Persists terminal engine execution events for operational queries.
 * <p>
 * Registered as a Spring bean so the CompileFlow starter collects it into the
 * engine configuration. Workbench Server uses synchronous event delivery so a
 * saturated best-effort event queue cannot silently bias monitoring and canary
 * samples.
 *
 * @author yusu
 */
@Component
public class ExecutionLogListener implements ProcessEventListener {
    private final ExecutionLogService logService;

    public ExecutionLogListener(ExecutionLogService logService) {
        this.logService = Objects.requireNonNull(logService, "logService");
    }

    private static ExecutionLogRecord toLogRecord(ProcessExecution execution, ExecutionAttribution attribution,
            long durationMs, long loggedAt, String status, String errorCode, String errorMessage) {
        var processVersion = execution.getProcessVersion();
        var admittedAlias = attribution.admittedAlias();
        return new ExecutionLogRecord(execution.getProcessCode(), status, durationMs, errorCode, errorMessage, loggedAt,
                execution.getInvocationId(), attribution.parentInvocationId(), attribution.callDepth(),
                execution.getTraceId(), execution.getNamespace(), attribution.modelType(), attribution.sourceDigest(),
                processVersion != null && admittedAlias == null ? processVersion.version() : null,
                processVersion == null ? null : processVersion.version(), routingSource(processVersion, admittedAlias),
                admittedAlias == null ? null : admittedAlias.alias(), attribution.aliasRevision());
    }

    private static String routingSource(ProcessRef.Version version, ProcessRef.Alias alias) {
        if (alias != null) {
            return "alias";
        }
        return version == null ? "definition" : "version";
    }

    @Override
    public boolean supports(ProcessEvent event) {
        return event instanceof ProcessEvent.ExecutionCompleted || event instanceof ProcessEvent.ExecutionFailed;
    }

    @Override
    public void onEvent(ProcessEvent event) {
        if (event instanceof ProcessEvent.ExecutionCompleted completed) {
            logService.persist(
                    toLogRecord(completed.execution(), completed.attribution(), completed.durationMs(),
                            completed.occurredAt().toEpochMilli(), "success", null, null));
            return;
        }
        if (event instanceof ProcessEvent.ExecutionFailed failed) {
            logService.persist(
                    toLogRecord(failed.execution(), failed.attribution(), failed.durationMs(),
                            failed.occurredAt().toEpochMilli(), "failed", failed.error().getCode(),
                            failed.error().getMessage()));
        }
    }
}
