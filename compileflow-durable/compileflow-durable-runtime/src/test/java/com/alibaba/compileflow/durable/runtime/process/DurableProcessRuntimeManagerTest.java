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
package com.alibaba.compileflow.durable.runtime.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.durable.api.error.DurableErrorCode;
import com.alibaba.compileflow.durable.api.error.DurableProcessException;
import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import com.alibaba.compileflow.durable.runtime.codec.RunContinuationCodec;
import com.alibaba.compileflow.durable.runtime.kernel.RunContinuation;
import com.alibaba.compileflow.durable.runtime.program.DurableProcessRuntimeCache;
import com.alibaba.compileflow.durable.runtime.program.DurableInterpretedProgramCompiler;
import com.alibaba.compileflow.durable.runtime.program.InMemoryDurableProcessRuntimeCache;
import com.alibaba.compileflow.durable.runtime.program.DurableProcessRuntime;
import com.alibaba.compileflow.durable.spi.admission.DurableVersionDefinitionSource;
import com.alibaba.compileflow.durable.spi.store.DurableCatalogStore;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig;
import com.alibaba.compileflow.engine.config.ProcessDefinitionConfig;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DurableProcessRuntimeManagerTest {
    private static final Instant NOW = Instant.parse("2026-08-16T00:00:00Z");
    private static final ProcessRef.Version PROCESS = ProcessRef.version("test", "prepare.once", "v1");
    private static final ProcessDefinition.Inline DEFINITION = definition(PROCESS.code());

    @Test
    void versionAdmissionRegistersAndLoadsOneStoredProcess() {
        MemoryCatalogStore store = new MemoryCatalogStore();
        InMemoryDurableProcessRuntimeCache cache = new InMemoryDurableProcessRuntimeCache(2);
        DurableProcessRuntimeManager manager = manager(store, cache, versionSource(PROCESS, DEFINITION));

        DurableStore.RunProcess first = manager.register(PROCESS);
        DurableStore.RunProcess second = manager.register(PROCESS);
        DurableProcessRuntime program = manager.requireRuntime(first.processId());

        assertThat(second).isEqualTo(first);
        assertThat(first.processVersion()).isEqualTo(PROCESS);
        assertThat(program.processId()).isEqualTo(first.processId());
        assertThat(program.processCode()).isEqualTo(PROCESS.code());
        assertThat(cache.get(first.processId())).contains(program);
        assertThat(store.processReads).hasValue(1);
    }

    @Test
    void definitionAndVersionAdmissionShareOneStoredProcess() {
        MemoryCatalogStore store = new MemoryCatalogStore();
        DurableProcessRuntimeManager manager =
                manager(store, new InMemoryDurableProcessRuntimeCache(2), versionSource(PROCESS, DEFINITION));

        DurableStore.RunProcess direct = manager.register(DEFINITION);
        DurableStore.RunProcess versioned = manager.register(PROCESS);

        assertThat(versioned.processId()).isEqualTo(direct.processId());
        assertThat(direct.processId().toString()).isEqualTo("cade5505-3e3f-86a8-9e9b-ac5179c781e0");
        assertThat(direct.processId().version()).isEqualTo(8);
        assertThat(direct.processVersion()).isNull();
        assertThat(versioned.processVersion()).isEqualTo(PROCESS);
        assertThat(store.byId).hasSize(1);
    }

    @Test
    void preparationIsCachedPerStoredProcessId() {
        MemoryCatalogStore store = new MemoryCatalogStore();
        DurableProcessRuntimeManager manager =
                manager(store, new InMemoryDurableProcessRuntimeCache(2), versionSource(PROCESS, DEFINITION));
        DurableStore.RunProcess process = manager.register(PROCESS);

        assertThat(manager.requireRuntime(process.processId())).isSameAs(manager.requireRuntime(process.processId()));
        assertThat(store.processReads).hasValue(1);
    }

    @Test
    void concurrentLoadFailurePreservesTheDurableErrorAndAllowsRetry() throws InterruptedException {
        BlockingMissingProcessStore store = new BlockingMissingProcessStore();
        DurableProcessRuntimeManager manager = manager(store, new InMemoryDurableProcessRuntimeCache(1));
        UUID processId = UUID.randomUUID();
        AtomicReference<Throwable> loaderFailure = new AtomicReference<>();
        AtomicReference<Throwable> waiterFailure = new AtomicReference<>();
        Thread loader = new Thread(() -> loaderFailure.set(captureFailure(() -> manager.requireRuntime(processId))),
                "durable-runtime-loader");
        Thread waiter = new Thread(() -> waiterFailure.set(captureFailure(() -> manager.requireRuntime(processId))),
                "durable-runtime-waiter");

        loader.start();
        assertThat(store.loadStarted.await(5, TimeUnit.SECONDS)).isTrue();
        waiter.start();
        try {
            awaitCompletableFutureWait(waiter);
        } finally {
            store.allowLoad.countDown();
            loader.join(5_000);
            waiter.join(5_000);
        }

        assertThat(loader.isAlive()).isFalse();
        assertThat(waiter.isAlive()).isFalse();
        assertThat(loaderFailure.get()).isInstanceOfSatisfying(DurableProcessException.class, failure -> {
            assertThat(failure.getErrorCode()).isEqualTo(DurableErrorCode.INTERNAL_ERROR);
            assertThat(failure).hasMessage("Stored Durable Process was not found");
        });
        assertThat(waiterFailure.get()).isSameAs(loaderFailure.get());
        assertThat(store.processReads).hasValue(1);

        assertThatThrownBy(() -> manager.requireRuntime(processId))
            .isInstanceOfSatisfying(DurableProcessException.class, failure -> {
                assertThat(failure.getErrorCode()).isEqualTo(DurableErrorCode.INTERNAL_ERROR);
                assertThat(failure).hasMessage("Stored Durable Process was not found");
            });
        assertThat(store.processReads).hasValue(2);
    }

    @Test
    void historicalVersionRecoversFromItsOwnStoredDefinition() {
        MemoryCatalogStore store = new MemoryCatalogStore();
        DurableVersionDefinitionSource source =
                version -> {
            String coordinate = version.version().equals("v1") ? "80,0" : "90,0";
            ProcessDefinition.Inline definition =
                    ProcessDefinition.inline(version.code(), definitionXml(version.code()).replace("80,0", coordinate));
            return Optional.of(new DurableVersionDefinitionSource.VersionDefinition(ProcessModelType.TBBPM, definition));
        };
        DurableProcessRuntimeManager manager = manager(store, new InMemoryDurableProcessRuntimeCache(1), source);
        ProcessRef.Version v1 = ProcessRef.version("test", "history", "v1");
        ProcessRef.Version v2 = ProcessRef.version("test", "history", "v2");
        DurableStore.RunProcess historical = manager.register(v1);
        DurableStore.RunProcess current = manager.register(v2);

        DurableProcessRuntimeManager restarted = manager(store, new InMemoryDurableProcessRuntimeCache(1), source);
        DurableProcessRuntime recovered = restarted.requireRuntime(historical.processId());

        assertThat(historical.processVersion()).isEqualTo(v1);
        assertThat(current.processId()).isNotEqualTo(historical.processId());
        assertThat(recovered.processId()).isEqualTo(historical.processId());
    }

    @Test
    void recoveryUsesStoredSemanticsWithoutConsultingTheAdmissionSource() {
        MemoryCatalogStore store = new MemoryCatalogStore();
        AtomicInteger sourceReads = new AtomicInteger();
        DurableVersionDefinitionSource source =
                version -> {
            sourceReads.incrementAndGet();
            return version.equals(PROCESS)
                    ? Optional.of(
                            new DurableVersionDefinitionSource.VersionDefinition(ProcessModelType.TBBPM, DEFINITION))
                    : Optional.empty();
        };
        DurableProcessRuntimeManager admitting = manager(store, new InMemoryDurableProcessRuntimeCache(1), source);
        DurableStore.RunProcess process = admitting.register(PROCESS);

        DurableProcessRuntimeManager recovering =
                manager(store, new InMemoryDurableProcessRuntimeCache(1), DurableVersionDefinitionSource.empty());
        DurableProcessRuntime recovered = recovering.requireRuntime(process.processId());

        assertThat(sourceReads).hasValue(1);
        assertThat(recovered.processId()).isEqualTo(process.processId());
        assertThatThrownBy(() -> recovering.register(PROCESS))
            .isInstanceOfSatisfying(DurableProcessException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DurableErrorCode.VERSION_NOT_FOUND));
    }

    @Test
    void changedConventionDefinitionGetsANewExactProcessIdentity() {
        MemoryCatalogStore store = new MemoryCatalogStore();
        DurableProcessRuntimeManager manager = manager(store, new InMemoryDurableProcessRuntimeCache(2));
        ProcessDefinition.Inline process = definition("convention.process");
        DurableStore.RunProcess first = manager.register(process);
        ProcessDefinition.Inline changed =
                ProcessDefinition.inline(process.code(), definitionXml(process.code()).replace("80,0", "90,0"));

        DurableStore.RunProcess second = manager.register(changed);

        assertThat(second.processId()).isNotEqualTo(first.processId());
        assertThat(first.processVersion()).isNull();
        assertThat(second.processVersion()).isNull();
        assertThat(first)
            .extracting(DurableStore.RunProcess::namespace, DurableStore.RunProcess::processCode)
            .containsExactly(ProcessRef.DEFAULT_NAMESPACE, process.code());
    }

    @Test
    void localDefinitionLoadingUsesTheDurableFailureBoundary() {
        DurableProcessRuntimeManager manager =
                manager(new MemoryCatalogStore(), new InMemoryDurableProcessRuntimeCache(1));

        assertThatThrownBy(() -> manager.register(ProcessDefinition.classpath("missing",
                "missing".replace(".", "/") + ".bpm")))
            .isInstanceOfSatisfying(DurableProcessException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DurableErrorCode.INVALID_ARGUMENT));
    }

    @Test
    void admissionPersistsDistinctCallSitesWhilePreparingSharedMembersOnce() {
        MemoryCatalogStore store = new MemoryCatalogStore();
        DurableProcessRuntimeManager manager = manager(store, new InMemoryDurableProcessRuntimeCache(8));
        ProcessDefinition.Inline rootDefinition = ProcessDefinition.inline("root",
                """
                <bpm code="root">
                  <start id="start" g="0,0,32,32"><transition to="first"/></start>
                  <bpmCall id="first" code="child" classpath="flows/child.bpm" g="50,0,32,32">
                    <transition to="second"/>
                  </bpmCall>
                  <bpmCall id="second" code="child" classpath="flows/child.bpm" g="100,0,32,32">
                    <transition to="end"/>
                  </bpmCall>
                  <end id="end" g="150,0,32,32"/>
                </bpm>
                """);
        DurableStore.RunProcess storedRoot = manager.register(rootDefinition);

        DurableStore.NewRun command = manager.createNewRun(ProcessRunId.random(), storedRoot, Map.of(), null);
        RunContinuation continuation =
                new RunContinuationCodec().decode(command.continuation().payload(), manager::requireRuntime);
        LinkedHashSet<UUID> recoveryProcessIds = new LinkedHashSet<>(continuation.processCallTargets().values());
        recoveryProcessIds.add(storedRoot.processId());

        assertThat(continuation.processCallTargets()).hasSize(3);
        assertThat(command.recoveryProcessIds()).containsExactlyInAnyOrderElementsOf(recoveryProcessIds);
        assertThat(continuation.processCallTargets().keySet())
            .extracting(RunContinuation.ProcessCallSite::callSiteId)
            .containsExactlyInAnyOrder("first", "second", "grandchild");
    }

    @Test
    void admissionRejectsMappingsOutsideTheExactChildContract() {
        MemoryCatalogStore store = new MemoryCatalogStore();
        DurableProcessRuntimeManager manager = manager(store, new InMemoryDurableProcessRuntimeCache(4));
        ProcessDefinition.Inline rootDefinition = ProcessDefinition.inline("root",
                """
                <bpm code="root">
                  <var name="request" dataType="java.lang.String" inOutType="param"/>
                  <start id="start" g="0,0,32,32"><transition to="child"/></start>
                  <bpmCall id="child" code="child" classpath="flows/child.bpm" g="50,0,32,32">
                    <input source="request" target="missing"/>
                    <transition to="end"/>
                  </bpmCall>
                  <end id="end" g="120,0,32,32"/>
                </bpm>
                """);
        DurableStore.RunProcess storedRoot = manager.register(rootDefinition);

        assertThatThrownBy(() -> manager.createNewRun(ProcessRunId.random(), storedRoot, Map.of("request", "value"),
                null))
            .isInstanceOfSatisfying(DurableProcessException.class, failure -> {
                assertThat(failure.getErrorCode()).isEqualTo(DurableErrorCode.INVALID_ARGUMENT);
                assertThat(failure).hasMessageContaining("input target 'missing' is not declared");
            });
    }

    @Test
    void versionedCallInheritsItsCallerNamespace() {
        MemoryCatalogStore store = new MemoryCatalogStore();
        ProcessRef.Version rootVersion = ProcessRef.version("commerce", "order", "v7");
        ProcessRef.Version childVersion = ProcessRef.version("commerce", "payment", "v3");
        ProcessDefinition.Inline root = ProcessDefinition.inline(rootVersion.code(),
                callDefinitionXml(rootVersion.code(), childVersion.code(), "version=\"v3\""));
        DurableVersionDefinitionSource source =
                version -> {
            if (version.equals(rootVersion)) {
                return Optional.of(
                        new DurableVersionDefinitionSource.VersionDefinition(ProcessModelType.TBBPM, root,
                                Map.of("call", childVersion)));
            }
            if (version.equals(childVersion)) {
                return Optional.of(
                        new DurableVersionDefinitionSource.VersionDefinition(ProcessModelType.TBBPM,
                                definition(childVersion.code())));
            }
            return Optional.empty();
        };
        DurableProcessRuntimeManager manager = manager(store, new InMemoryDurableProcessRuntimeCache(4), source);

        DurableStore.RunProcess storedRoot = manager.register(rootVersion);
        manager.createNewRun(ProcessRunId.random(), storedRoot, Map.of(), null);

        assertThat(store.byId.values()).anySatisfy(child -> assertThat(child.processCode()).isEqualTo("payment"));
    }

    @Test
    void directDefinitionMayUsePublishedChildVersion() {
        MemoryCatalogStore store = new MemoryCatalogStore();
        ProcessRef.Version childVersion = ProcessRef.version(ProcessRef.DEFAULT_NAMESPACE, "payment", "v3");
        DurableVersionDefinitionSource source =
                version -> version.equals(childVersion)
                ? Optional.of(
                        new DurableVersionDefinitionSource.VersionDefinition(ProcessModelType.TBBPM,
                                definition(childVersion.code())))
                : Optional.empty();
        DurableProcessRuntimeManager manager = manager(store, new InMemoryDurableProcessRuntimeCache(2), source);
        ProcessDefinition.Inline root =
                ProcessDefinition.inline("order", callDefinitionXml("order", "payment", "version=\"v3\""));
        DurableStore.RunProcess storedRoot = manager.register(root);

        DurableStore.NewRun command = manager.createNewRun(ProcessRunId.random(), storedRoot, Map.of(), null);

        assertThat(command.recoveryProcessIds()).hasSize(2);
        assertThat(store.byId.values()).anySatisfy(child -> assertThat(child.processCode()).isEqualTo("payment"));
    }

    @Test
    void versionedParentCannotUseResourceChild() {
        ProcessRef.Version rootVersion = ProcessRef.version("commerce", "order", "v7");
        ProcessDefinition.Inline root = ProcessDefinition.inline(rootVersion.code(),
                callDefinitionXml(rootVersion.code(), "child", "classpath=\"flows/child.bpm\""));
        MemoryCatalogStore store = new MemoryCatalogStore();
        DurableProcessRuntimeManager manager =
                manager(store, new InMemoryDurableProcessRuntimeCache(2), versionSource(rootVersion, root));
        assertThatThrownBy(() -> manager.register(rootVersion))
            .isInstanceOfSatisfying(DurableProcessException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DurableErrorCode.UNSUPPORTED_PROCESS));
    }

    @Test
    void versionedBpmnParentCannotUseResourceChild() {
        ProcessRef.Version rootVersion = ProcessRef.version("commerce", "bpmn.parent", "v1");
        ProcessDefinition.Inline root = ProcessDefinition.inline(rootVersion.code(),
                bpmnCallDefinitionXml(rootVersion.code(), "bpmn.child", "flows/bpmn-child.bpmn"));
        MemoryCatalogStore store = new MemoryCatalogStore();
        DurableProcessRuntimeManager manager = manager(store, new InMemoryDurableProcessRuntimeCache(4),
                versionSource(rootVersion, ProcessModelType.BPMN, root));
        assertThatThrownBy(() -> manager.register(rootVersion))
            .isInstanceOfSatisfying(DurableProcessException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DurableErrorCode.UNSUPPORTED_PROCESS));
    }

    @Test
    void versionedTbbpmParentCannotUseResourceChild() {
        ProcessRef.Version rootVersion = ProcessRef.version("commerce", "tbbpm.parent", "v1");
        ProcessDefinition.Inline root = ProcessDefinition.inline(rootVersion.code(),
                callDefinitionXml(rootVersion.code(), "child", "classpath=\"flows/child.bpm\""));
        MemoryCatalogStore store = new MemoryCatalogStore();
        ProcessEngineConfig config =
                ProcessEngineConfig
            .bpmnBuilder()
            .classLoader(getClass().getClassLoader())
            .discoverPlugins(false)
            .build();
        DurableProcessRuntimeManager manager = manager(store, new InMemoryDurableProcessRuntimeCache(4), config,
                versionSource(rootVersion, ProcessModelType.TBBPM, root));
        assertThatThrownBy(() -> manager.register(rootVersion))
            .isInstanceOfSatisfying(DurableProcessException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DurableErrorCode.UNSUPPORTED_PROCESS));
    }

    @Test
    void directDefinitionUsesTheBoundModelType() {
        MemoryCatalogStore store = new MemoryCatalogStore();
        ProcessEngineConfig config =
                ProcessEngineConfig
            .bpmnBuilder()
            .classLoader(getClass().getClassLoader())
            .discoverPlugins(false)
            .build();
        DurableProcessRuntimeManager manager =
                manager(store, new InMemoryDurableProcessRuntimeCache(4), config, DurableVersionDefinitionSource.empty());
        ProcessDefinition.Inline definition = ProcessDefinition.inline("bpmn.local",
                """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             targetNamespace="https://compileflow.alibaba.com/test">
                  <process id="bpmn.local" isExecutable="true">
                    <startEvent id="start"/>
                    <endEvent id="end"/>
                    <sequenceFlow id="toEnd" sourceRef="start" targetRef="end"/>
                  </process>
                </definitions>
                """);
        DurableStore.RunProcess stored = manager.register(definition);

        assertThat(store.findProcess(stored.processId()))
            .get()
            .extracting(DurableStore.StoredProcess::modelType)
            .isEqualTo(ProcessModelType.BPMN);
    }

    @Test
    void processCallDepthUsesTheLongestPathForSharedDescendants() {
        ProcessRef.Version root = ProcessRef.version("test", "depth-root", "v1");
        ProcessRef.Version first = ProcessRef.version("test", "depth-first", "v1");
        ProcessRef.Version second = ProcessRef.version("test", "depth-second", "v1");
        ProcessRef.Version bridge = ProcessRef.version("test", "depth-bridge", "v1");
        ProcessRef.Version shared = ProcessRef.version("test", "depth-shared", "v1");
        ProcessRef.Version leaf = ProcessRef.version("test", "depth-leaf", "v1");
        Map<ProcessRef.Version, DurableVersionDefinitionSource.VersionDefinition> definitions = Map.of(root,
                versionDefinition(root, twoCallDefinition(root.code(), first.code(), second.code()),
                        Map.of("first", first, "second", second)), first,
                versionDefinition(first, callDefinitionXml(first.code(), shared.code(), "version=\"v1\""),
                        Map.of("call", shared)), second,
                versionDefinition(second, callDefinitionXml(second.code(), bridge.code(), "version=\"v1\""),
                        Map.of("call", bridge)), bridge,
                versionDefinition(bridge, callDefinitionXml(bridge.code(), shared.code(), "version=\"v1\""),
                        Map.of("call", shared)), shared,
                versionDefinition(shared, callDefinitionXml(shared.code(), leaf.code(), "version=\"v1\""),
                        Map.of("call", leaf)), leaf,
                versionDefinition(leaf, definition(leaf.code()).content(), Map.of()));
        ProcessEngineConfig config = ProcessEngineConfig
            .tbbpmBuilder()
            .classLoader(getClass().getClassLoader())
            .discoverPlugins(false)
            .maxCallDepth(4)
            .build();
        MemoryCatalogStore store = new MemoryCatalogStore();
        DurableVersionDefinitionSource source = requested -> Optional.ofNullable(definitions.get(requested));
        DurableProcessRuntimeManager manager = manager(store, new InMemoryDurableProcessRuntimeCache(8), config, source);

        DurableStore.RunProcess storedRoot = manager.register(root);

        assertThatThrownBy(() -> manager.createNewRun(ProcessRunId.random(), storedRoot, Map.of(), null))
            .isInstanceOfSatisfying(DurableProcessException.class, failure -> {
                assertThat(failure.getErrorCode()).isEqualTo(DurableErrorCode.UNSUPPORTED_PROCESS);
                assertThat(failure).hasMessageContaining(root.code(), shared.code(), leaf.code());
            });
    }

    @Test
    void rejectsDefinitionsThatExceedTheDurableLimitBeforeParsing() {
        ProcessEngineConfig config = ProcessEngineConfig
            .tbbpmBuilder()
            .definitions(ProcessDefinitionConfig.builder().maxBytes(DurableStore.MAX_DEFINITION_BYTES + 1024).build())
            .build();
        DurableProcessRuntimeManager manager = manager(new MemoryCatalogStore(),
                new InMemoryDurableProcessRuntimeCache(1), config, DurableVersionDefinitionSource.empty());
        ProcessDefinition definition = ProcessDefinition.inline("too.large",
                "<bpm code=\"too.large\">" + " ".repeat(DurableStore.MAX_DEFINITION_BYTES) + "</bpm>");

        assertThatThrownBy(() -> manager.register(definition))
            .isInstanceOfSatisfying(DurableProcessException.class, failure -> {
                assertThat(failure.getErrorCode()).isEqualTo(DurableErrorCode.UNSUPPORTED_PROCESS);
                assertThat(failure).hasMessageContaining("Durable size limit");
            });
    }

    private DurableProcessRuntimeManager manager(DurableCatalogStore store, DurableProcessRuntimeCache cache) {
        return manager(store, cache, DurableVersionDefinitionSource.empty());
    }

    private DurableProcessRuntimeManager manager(DurableCatalogStore store, DurableProcessRuntimeCache cache,
            DurableVersionDefinitionSource versionSource) {
        ProcessEngineConfig config =
                ProcessEngineConfig
            .tbbpmBuilder()
            .classLoader(getClass().getClassLoader())
            .discoverPlugins(false)
            .build();
        return manager(store, cache, config, versionSource);
    }

    private DurableProcessRuntimeManager manager(DurableCatalogStore store, DurableProcessRuntimeCache cache,
            ProcessEngineConfig config, DurableVersionDefinitionSource versionSource) {
        return new DurableProcessRuntimeManager(store, cache,
                new DurableInterpretedProgramCompiler(JavaDiagnosticsConfig.defaults()), config, versionSource);
    }

    private static DurableVersionDefinitionSource versionSource(ProcessRef.Version version,
            ProcessDefinition.Inline definition) {
        return versionSource(version, ProcessModelType.TBBPM, definition);
    }

    private static DurableVersionDefinitionSource versionSource(ProcessRef.Version version, ProcessModelType modelType,
            ProcessDefinition.Inline definition) {
        return requested -> requested.equals(version)
                ? Optional.of(new DurableVersionDefinitionSource.VersionDefinition(modelType, definition))
                : Optional.empty();
    }

    private static ProcessDefinition.Inline definition(String code) {
        return ProcessDefinition.inline(code, definitionXml(code));
    }

    private static String definitionXml(String code) {
        return """
            <bpm code="%s">
              <start id="start" g="0,0,32,32"><transition to="end"/></start>
              <end id="end" g="80,0,32,32"/>
            </bpm>
            """
            .formatted(code);
    }

    private static String callDefinitionXml(String code, String childCode, String target) {
        return """
            <bpm code="%s">
              <start id="start" g="0,0,32,32"><transition to="call"/></start>
              <bpmCall id="call" code="%s" %s g="40,0,80,32"><transition to="end"/></bpmCall>
              <end id="end" g="140,0,32,32"/>
            </bpm>
            """
            .formatted(code, childCode, target);
    }

    private static String twoCallDefinition(String code, String first, String second) {
        return """
            <bpm code="%s">
              <start id="start" g="0,0,32,32"><transition to="first"/></start>
              <bpmCall id="first" code="%s" version="v1" g="40,0,80,32">
                <transition to="second"/>
              </bpmCall>
              <bpmCall id="second" code="%s" version="v1" g="140,0,80,32">
                <transition to="end"/>
              </bpmCall>
              <end id="end" g="240,0,32,32"/>
            </bpm>
            """
            .formatted(code, first, second);
    }

    private static DurableVersionDefinitionSource.VersionDefinition versionDefinition(ProcessRef.Version version,
            String definition, Map<String, ProcessRef.Version> callBindings) {
        return new DurableVersionDefinitionSource.VersionDefinition(ProcessModelType.TBBPM,
                ProcessDefinition.inline(version.code(), definition), callBindings);
    }

    private static Throwable captureFailure(Runnable operation) {
        try {
            operation.run();
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private static void awaitCompletableFutureWait(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            for (StackTraceElement frame : thread.getStackTrace()) {
                if (frame.getClassName().equals(CompletableFuture.class.getName())) {
                    return;
                }
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Concurrent runtime loader did not wait for the active load");
    }

    private static String bpmnCallDefinitionXml(String code, String childCode, String classpath) {
        return """
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:cf="http://www.compileflow.org"
                         targetNamespace="https://compileflow.alibaba.com/test">
              <process id="%s" isExecutable="true">
                <startEvent id="start"/>
                <callActivity id="call" calledElement="%s" cf:classpath="%s"/>
                <endEvent id="end"/>
                <sequenceFlow id="toCall" sourceRef="start" targetRef="call"/>
                <sequenceFlow id="toEnd" sourceRef="call" targetRef="end"/>
              </process>
            </definitions>
            """
            .formatted(code, childCode, classpath);
    }

    private static final class MemoryCatalogStore implements DurableCatalogStore {
        private final Map<UUID, DurableStore.StoredProcess> byId = new LinkedHashMap<>();
        private final AtomicInteger processReads = new AtomicInteger();

        @Override
        public DurableStore.StoredProcess registerProcess(DurableStore.ProcessRegistration registration) {
            DurableStore.StoredProcess stored = new DurableStore.StoredProcess(registration.processId(),
                    registration.processCode(), registration.modelType(), registration.definitionBytes(),
                    registration.definitionDigest(), NOW);
            DurableStore.StoredProcess existing = byId.putIfAbsent(registration.processId(), stored);
            if (existing != null) {
                return existing;
            }
            byId.putIfAbsent(registration.processId(), stored);
            return stored;
        }

        @Override
        public Optional<DurableStore.StoredProcess> findProcess(UUID processId) {
            processReads.incrementAndGet();
            return Optional.ofNullable(byId.get(processId));
        }

        @Override
        public DurableStore.ProcessRuntimeDemandPage listProcessRuntimeDemand(
                DurableStore.ProcessRuntimeDemandQuery query) {
            return new DurableStore.ProcessRuntimeDemandPage(java.util.List.of(), null);
        }
    }

    private static final class BlockingMissingProcessStore implements DurableCatalogStore {
        private final CountDownLatch loadStarted = new CountDownLatch(1);
        private final CountDownLatch allowLoad = new CountDownLatch(1);
        private final AtomicInteger processReads = new AtomicInteger();

        @Override
        public DurableStore.StoredProcess registerProcess(DurableStore.ProcessRegistration registration) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<DurableStore.StoredProcess> findProcess(UUID processId) {
            processReads.incrementAndGet();
            loadStarted.countDown();
            try {
                if (!allowLoad.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Timed out waiting to finish the runtime load");
                }
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Runtime load was interrupted", failure);
            }
            return Optional.empty();
        }

        @Override
        public DurableStore.ProcessRuntimeDemandPage listProcessRuntimeDemand(
                DurableStore.ProcessRuntimeDemandQuery query) {
            return new DurableStore.ProcessRuntimeDemandPage(java.util.List.of(), null);
        }
    }
}
