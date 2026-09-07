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
package com.alibaba.compileflow.durable.runtime;

import com.alibaba.compileflow.durable.runtime.action.DurableActionInvoker;
import com.alibaba.compileflow.durable.runtime.machine.DurableProcessCompiler;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics;
import com.alibaba.compileflow.durable.runtime.process.DurableProcessRuntimeManager;
import com.alibaba.compileflow.durable.runtime.program.DurableInterpretedProgramCompiler;
import com.alibaba.compileflow.durable.runtime.program.DurableJavaProgramCompiler;
import com.alibaba.compileflow.durable.runtime.program.DurableProgramCompiler;
import com.alibaba.compileflow.durable.runtime.program.InMemoryDurableProcessRuntimeCache;
import com.alibaba.compileflow.durable.runtime.worker.DurableWorkerIdentity;
import com.alibaba.compileflow.durable.runtime.worker.DurableEffectWorker;
import com.alibaba.compileflow.durable.runtime.worker.DurableEffectWorkerOptions;
import com.alibaba.compileflow.durable.runtime.worker.DurableLeaseRenewer;
import com.alibaba.compileflow.durable.runtime.worker.DurableOutboxPublisher;
import com.alibaba.compileflow.durable.runtime.worker.DurableOutboxPublisherOptions;
import com.alibaba.compileflow.durable.runtime.worker.DurableProcessRuntimeLoadWorker;
import com.alibaba.compileflow.durable.runtime.worker.DurableRetentionWorker;
import com.alibaba.compileflow.durable.runtime.worker.DurableTurnWorker;
import com.alibaba.compileflow.durable.runtime.worker.DurableTurnWorkerOptions;
import com.alibaba.compileflow.durable.runtime.worker.DurableWorkerCoordinator;
import com.alibaba.compileflow.durable.runtime.worker.SecureWaitTokenIssuer;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import com.alibaba.compileflow.engine.config.ProcessRuntimeMode;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Canonical assembly of a node-local Durable engine, independent of application frameworks.
 *
 * <p>The returned engine accepts application operations immediately. Call its no-argument
 * {@code start()} to start configured Workers; frameworks may choose their own startup timing.
 *
 * @author yusu
 */
public final class DurableProcessEngineFactory {
    private static final Duration NOT_READY_BACKOFF = Duration.ofSeconds(1);

    private DurableProcessEngineFactory() {
    }

    public static DefaultDurableProcessEngine create(DurableProcessEngineConfig configuration) {
        DurableProcessEngineConfig config = Objects.requireNonNull(configuration, "configuration");
        DurableStore store = config.getStore();
        InMemoryDurableProcessRuntimeCache cache = new InMemoryDurableProcessRuntimeCache(config.getCacheMaxSize());
        ScriptExecutorRegistry scripts =
                ScriptExecutorRegistry.configured(config.getClassLoader(), config.getScriptExecutors());
        DurableLeaseRenewer leases = null;
        try {
            DurableRuntimeMetrics metrics = new DurableRuntimeMetrics();
            DurableProgramCompiler compiler = config.getRuntimeMode() == ProcessRuntimeMode.INTERPRETED
                    ? new DurableInterpretedProgramCompiler(config.getJavaDiagnostics())
                    : new DurableJavaProgramCompiler(config.getJavaDiagnostics());
            DurableProcessRuntimeManager processes = new DurableProcessRuntimeManager(store, cache,
                    new DurableProcessCompiler(scripts), compiler, config.getDefinitionConfig(), config.getClassLoader(),
                    config.getMaxCallDepth(), config.getVersionDefinitionSource());
            AliasAdmission aliases = config.getAliasStateSource() == null
                    ? null
                    : new AliasAdmission(config.getAliasStateSource(), config.getAliasTargetingPolicies());
            DurableWorkerCoordinator workers = null;
            if (config.getWorker().enabled()) {
                DurableProcessEngineConfig.Worker worker = config.getWorker();
                DurableProcessEngineConfig.Outbox outbox = config.getOutbox();
                DurableProcessEngineConfig.Maintenance maintenance = config.getMaintenance();
                DurableProcessEngineConfig.Retention retention = config.getRetention();
                DurableWorkerIdentity identity = DurableWorkerIdentity.create(worker.id());
                DurableActionInvoker actions =
                        new DurableActionInvoker(config.getComponentResolver(), scripts, config.getClassLoader());
                leases = new DurableLeaseRenewer(store, worker.leaseDuration(), metrics);
                DurableTurnWorker turns = new DurableTurnWorker(store, processes, cache, actions,
                        config.getWaitDescriptionProvider(), new SecureWaitTokenIssuer(),
                        new DurableTurnWorkerOptions(identity.worker("turn"), worker.turnFaultBackoff(),
                                worker.turnMaxSteps(), worker.maxActiveIterations()), leases, metrics);
                DurableEffectWorker effects = new DurableEffectWorker(store, cache, actions,
                        new DurableEffectWorkerOptions(identity.worker("effect"), NOT_READY_BACKOFF), leases, metrics);
                DurableProcessRuntimeLoadWorker loads =
                        new DurableProcessRuntimeLoadWorker(store, processes, cache, NOT_READY_BACKOFF, metrics);
                DurableOutboxPublisher publisher = config.getOutboxSink() == null
                        ? null
                        : new DurableOutboxPublisher(store, config.getOutboxSink(),
                                new DurableOutboxPublisherOptions(identity.worker("outbox"), outbox.initialDelay(),
                                        outbox.maxDelay(), outbox.maxAttempts()), leases, metrics);
                DurableRetentionWorker retentionWorker = !retention.enabled()
                        ? null
                        : new DurableRetentionWorker(store, retention.terminalRun(), retention.unusedProcess(),
                                retention.consumedOccurrence(), metrics);
                workers = new DurableWorkerCoordinator(store, loads, turns, effects, Optional.ofNullable(publisher),
                        Optional.ofNullable(retentionWorker), worker.idlePollDelay(), maintenance.interval(),
                        retention.interval(), maintenance.batchSize(), worker.turnConcurrency(),
                        worker.effectConcurrency(), outbox.concurrency(), metrics, config.getShutdownTimeout());
            }
            return new DefaultDurableProcessEngine(store, processes, aliases, cache, scripts, leases, workers, metrics,
                    config.getOutboxSink() != null);
        } catch (RuntimeException | Error failure) {
            if (leases != null) {
                try {
                    leases.close();
                } catch (RuntimeException | Error cleanup) {
                    failure.addSuppressed(cleanup);
                }
            }
            try {
                scripts.close();
            } catch (RuntimeException | Error cleanup) {
                failure.addSuppressed(cleanup);
            }
            try {
                cache.clear();
            } catch (RuntimeException | Error cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }
}
