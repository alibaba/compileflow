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
package com.alibaba.compileflow.durable.runtime.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import com.alibaba.compileflow.durable.api.model.RunRetryCode;
import com.alibaba.compileflow.durable.runtime.kernel.ContinuationSnapshot;
import com.alibaba.compileflow.durable.runtime.kernel.BoundaryKind;
import com.alibaba.compileflow.durable.runtime.kernel.FrontierStepResult;
import com.alibaba.compileflow.durable.runtime.kernel.MachineTurnResult;
import com.alibaba.compileflow.durable.runtime.kernel.OccurrenceKey;
import com.alibaba.compileflow.durable.runtime.codec.DurableValueSerializer;
import com.alibaba.compileflow.durable.runtime.codec.DurableKernelJsonCodec;
import com.alibaba.compileflow.durable.runtime.codec.RunContinuationCodec;
import com.alibaba.compileflow.durable.runtime.kernel.RunContinuation;
import com.alibaba.compileflow.durable.runtime.kernel.ResumePoint;
import com.alibaba.compileflow.durable.runtime.action.DurableActionInvoker;
import com.alibaba.compileflow.durable.runtime.program.DurableCompilerTestSupport;
import com.alibaba.compileflow.durable.runtime.program.DurableJavaProgramCompiler;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics.Operation;
import com.alibaba.compileflow.durable.runtime.observability.DurableRuntimeMetrics.Outcome;
import com.alibaba.compileflow.durable.runtime.process.DurableProcessRuntimeManager;
import com.alibaba.compileflow.durable.runtime.program.InMemoryDurableProcessRuntimeCache;
import com.alibaba.compileflow.durable.runtime.program.DurableProcessRuntime;
import com.alibaba.compileflow.durable.runtime.program.DurableProcessRuntimeCache;
import com.alibaba.compileflow.durable.spi.admission.DurableVersionDefinitionSource;
import com.alibaba.compileflow.durable.spi.wait.DurableWaitDescriptionProvider;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import com.alibaba.compileflow.engine.ProcessDefinitionDigest;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.tbbpm.parser.TbbpmXmlParser;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class DurableTurnWorkerTest {
    private static final ProcessRunId RUN_ID = new ProcessRunId("00000000-0000-0000-0000-000000000101");
    private static final UUID LEASE_TOKEN = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");
    private static final Duration LEASE_DURATION = Duration.ofHours(1);
    private static final Duration FAULT_BACKOFF = Duration.ofSeconds(5);
    private static final String WAIT_TOKEN = "opaque-turn-worker-test-token";
    private static final String WAIT_TOKEN_DIGEST = "a".repeat(64);

    @Test
    void skipsStoreWhenNoProgramIsReadyAndPassesExactReadySetWhenClaiming() {
        CapturingStore noReady = new CapturingStore(Optional.empty(), true);
        assertThat(run(noReady, new InMemoryDurableProcessRuntimeCache(), new DurableRuntimeMetrics())).isFalse();
        assertThat(noReady.claimRequest).isNull();

        DurableProcessRuntime program = loadProgram("turn.empty", completedFlow("turn.empty"));
        CapturingStore noRun = new CapturingStore(Optional.empty(), true);
        assertThat(run(noRun, cache(program), new DurableRuntimeMetrics())).isFalse();
        assertThat(noRun.claimRequest.readyRootProcessIds()).containsExactly(program.processId());
        assertThat(noRun.mutation).isNull();
    }

    @Test
    void completedTurnCommitsResultAndReportsStaleCompletionAsLeaseLoss() {
        DurableProcessRuntime program = loadProgram("turn.complete", completedFlow("turn.complete"));
        DurableStore.RunClaim claim = startClaim(program, false);

        CapturingStore committed = new CapturingStore(Optional.of(claim), true);
        DurableRuntimeMetrics successMetrics = new DurableRuntimeMetrics();
        assertThat(run(committed, cache(program), successMetrics)).isTrue();
        assertThat(committed.mutation).isEqualTo("commitTurn");
        assertThat(((DurableStore.TurnCommit) committed.value).state()).isInstanceOf(DurableStore.SucceededTurn.class);
        assertThat(successMetrics.count(Operation.TURN, Outcome.CLAIMED)).isOne();
        assertThat(successMetrics.count(Operation.TURN, Outcome.SUCCESS)).isOne();

        CapturingStore stale = new CapturingStore(Optional.of(claim), false);
        DurableRuntimeMetrics staleMetrics = new DurableRuntimeMetrics();
        assertThat(run(stale, cache(program), staleMetrics)).isTrue();
        assertThat(staleMetrics.count(Operation.TURN, Outcome.LEASE_LOST)).isOne();
        assertThat(staleMetrics.count(Operation.TURN, Outcome.SUCCESS)).isZero();
    }

    @Test
    void turnBudgetExhaustionCommitsProgressAsRunnableInsteadOfRetryingTheSameFault() {
        DurableProcessRuntime program = loadProgram("turn.yield", completedFlow("turn.yield"));
        CapturingStore first = new CapturingStore(Optional.of(startClaim(program, false)), true);

        assertThat(run(first, cache(program), new DurableRuntimeMetrics(), 1)).isTrue();
        assertThat(first.mutation).isEqualTo("commitTurn");
        DurableStore.TurnCommit yieldedTurn = (DurableStore.TurnCommit) first.value;
        DurableStore.RunnableTurn yielded = (DurableStore.RunnableTurn) yieldedTurn.state();
        ContinuationSnapshot checkpoint =
                decode(yielded.continuation(), cache(program)).requireInvocation(0).continuation();
        assertThat(checkpoint.resumePoint()).isEqualTo(ResumePoint.beforeElement("end"));

        DurableStore.RunClaim resumed = new DurableStore.RunClaim(lease(), runProcess(program), yielded.continuation(),
                0L, List.of(), false, NOW.plus(LEASE_DURATION));
        CapturingStore second = new CapturingStore(Optional.of(resumed), true);
        assertThat(run(second, cache(program), new DurableRuntimeMetrics(), 1)).isTrue();
        assertThat(((DurableStore.TurnCommit) second.value).state()).isInstanceOf(DurableStore.SucceededTurn.class);
    }

    @Test
    void waitCommitContainsOnlyDigestInAuthorityAndRawTokenInOutboxPayload() {
        DurableProcessRuntime program = loadProgram("turn.wait", waitFlow());
        CapturingStore store = new CapturingStore(Optional.of(startClaim(program, false)), true);

        assertThat(run(store, cache(program), new DurableRuntimeMetrics())).isTrue();
        assertThat(store.mutation).isEqualTo("commitTurn");
        DurableStore.TurnCommit turn = (DurableStore.TurnCommit) store.value;
        DurableStore.WaitCommit commit = (DurableStore.WaitCommit) turn.issuedOccurrences().get(0);
        assertThat(commit.occurrenceSequence()).isOne();
        assertThat(commit.elementId()).isEqualTo("approval");
        assertThat(commit.event()).isEqualTo("approved");
        assertThat(commit.tokenDigest()).isEqualTo(WAIT_TOKEN_DIGEST);
        DurableStore.WaitingTurn waiting = (DurableStore.WaitingTurn) turn.state();
        assertThat(decode(waiting.continuation(), cache(program)).requireInvocation(0).continuation().resumePoint())
            .isEqualTo(ResumePoint.afterElement("approval"));

        Map<String, Object> payload = new DurableKernelJsonCodec().decode(commit.outboxPayload().payload());
        assertThat(payload)
            .containsEntry("runId", RUN_ID.value())
            .containsEntry("occurrenceSequence", 1)
            .containsEntry("elementId", "approval")
            .containsEntry("event", "approved")
            .containsEntry("waitToken", WAIT_TOKEN);
        assertThat(new String(waiting.continuation().payload(), StandardCharsets.UTF_8))
            .doesNotContain(WAIT_TOKEN)
            .doesNotContain(WAIT_TOKEN_DIGEST);
    }

    @Test
    void timerAndEffectBecomeTypedAtomicBoundaryCommits() {
        DurableProcessRuntime timer = loadProgram("turn.timer", timerFlow());
        CapturingStore timerStore = new CapturingStore(Optional.of(startClaim(timer, false)), true);
        assertThat(run(timerStore, cache(timer), new DurableRuntimeMetrics())).isTrue();
        assertThat(timerStore.mutation).isEqualTo("commitTurn");
        DurableStore.TurnCommit timerTurn = (DurableStore.TurnCommit) timerStore.value;
        DurableStore.TimerCommit timerCommit = (DurableStore.TimerCommit) timerTurn.issuedOccurrences().get(0);
        assertThat(timerCommit.occurrenceSequence()).isOne();
        assertThat(timerCommit.elementId()).isEqualTo("cooling");
        assertThat(timerCommit.delay()).isEqualTo(Duration.ofMinutes(5));
        assertThat(timerCommit.dueAt()).isNull();

        DurableProcessRuntime effect = loadProgram("turn.effect", effectFlow());
        CapturingStore effectStore = new CapturingStore(Optional.of(startClaim(effect, false)), true);
        assertThat(run(effectStore, cache(effect), new DurableRuntimeMetrics())).isTrue();
        assertThat(effectStore.mutation).isEqualTo("commitTurn");
        DurableStore.TurnCommit effectTurn = (DurableStore.TurnCommit) effectStore.value;
        DurableStore.EffectCommit effectCommit = (DurableStore.EffectCommit) effectTurn.issuedOccurrences().get(0);
        assertThat(effectCommit.occurrenceSequence()).isOne();
        assertThat(effectCommit.elementId()).isEqualTo("externalCall");
        assertThat(effect.valueSerializer().decodeEffectInput("externalCall", effectCommit.input().payload())).isEmpty();
    }

    @Test
    void processCallPushesAnExactChildFrameInTheSameRun() {
        DurableProcessRuntime parent = loadProgram("turn.child", childFlow());
        DurableProcessRuntime child = loadProgram("child.flow", childWaitFlow());
        InMemoryDurableProcessRuntimeCache programs = cache(parent, child);
        CapturingStore store = new CapturingStore(Optional.of(
                        startClaim(parent, false,
                                Map.of(new RunContinuation.ProcessCallSite(parent.processId(), "call"),
                                        child.processId()))), true);
        store.addProcess(child, childWaitFlow());

        assertThat(run(store, programs, new DurableRuntimeMetrics(), 10_000)).isTrue();

        assertThat(store.mutation).isEqualTo("commitTurn");
        DurableStore.TurnCommit turn = (DurableStore.TurnCommit) store.value;
        assertThat(turn.state()).isInstanceOf(DurableStore.WaitingTurn.class);
        DurableStore.WaitCommit wait = (DurableStore.WaitCommit) turn.issuedOccurrences().get(0);
        assertThat(wait.processId()).isEqualTo(child.processId());
        assertThat(wait.processInvocationId()).isOne();
        RunContinuation continuation = decode(((DurableStore.WaitingTurn) turn.state()).continuation(), programs);
        assertThat(continuation.invocations())
            .extracting(RunContinuation.ProcessInvocation::processId)
            .containsExactly(parent.processId(), child.processId());
    }

    @Test
    void pinsEveryCalledProcessForTheTurnWhenTheSharedCacheIsSmallerThanTheCallGraph() {
        DurableProcessRuntime parent = loadProgram("turn.child", childFlow());
        DurableProcessRuntime child = loadProgram("child.flow", childWaitFlow());
        InMemoryDurableProcessRuntimeCache programs = new InMemoryDurableProcessRuntimeCache(1);
        programs.put(parent);
        CapturingStore store = new CapturingStore(Optional.of(
                        startClaim(parent, false,
                                Map.of(new RunContinuation.ProcessCallSite(parent.processId(), "call"),
                                        child.processId()))), true);
        store.addProcess(child, childWaitFlow());

        assertThat(run(store, programs, new DurableRuntimeMetrics(), 10_000)).isTrue();

        assertThat(store.mutation).isEqualTo("commitTurn");
        DurableStore.TurnCommit turn = (DurableStore.TurnCommit) store.value;
        assertThat(turn.state()).isInstanceOf(DurableStore.WaitingTurn.class);
        assertThat(turn.issuedOccurrences())
            .singleElement()
            .extracting(DurableStore.OccurrenceCommit::processId)
            .isEqualTo(child.processId());
    }

    @Test
    void processCallAndChildShareOneTurnBudget() {
        DurableProcessRuntime parent = loadProgram("turn.child", childFlow());
        DurableProcessRuntime child = loadProgram("child.flow", childWaitFlow());
        InMemoryDurableProcessRuntimeCache programs = cache(parent, child);
        CapturingStore store = new CapturingStore(Optional.of(
                        startClaim(parent, false,
                                Map.of(new RunContinuation.ProcessCallSite(parent.processId(), "call"),
                                        child.processId()))), true);
        store.addProcess(child, childWaitFlow());

        assertThat(run(store, programs, new DurableRuntimeMetrics(), 2)).isTrue();

        DurableStore.TurnCommit turn = (DurableStore.TurnCommit) store.value;
        assertThat(turn.state()).isInstanceOf(DurableStore.RunnableTurn.class);
        assertThat(turn.issuedOccurrences()).isEmpty();
        RunContinuation continuation = decode(((DurableStore.RunnableTurn) turn.state()).continuation(), programs);
        assertThat(continuation.invocations())
            .extracting(RunContinuation.ProcessInvocation::processId)
            .containsExactly(parent.processId(), child.processId());
        assertThat(continuation.requireInvocation(1).continuation().resumePoint()).isEqualTo(ResumePoint.start());
    }

    @Test
    void preservesAChildReturnWhenItExhaustsTheTurnBudget() {
        DurableProcessRuntime parent = loadProgram("turn.child", childFlow());
        DurableProcessRuntime child = loadProgram("child.flow", completedFlow("child.flow"));
        InMemoryDurableProcessRuntimeCache programs = cache(parent, child);
        CapturingStore store = new CapturingStore(Optional.of(
                        startClaim(parent, false,
                                Map.of(new RunContinuation.ProcessCallSite(parent.processId(), "call"),
                                        child.processId()))), true);
        store.addProcess(child, completedFlow("child.flow"));

        assertThat(run(store, programs, new DurableRuntimeMetrics(), 4)).isTrue();

        DurableStore.TurnCommit turn = (DurableStore.TurnCommit) store.value;
        assertThat(turn.state()).isInstanceOf(DurableStore.RunnableTurn.class);
        RunContinuation continuation = decode(((DurableStore.RunnableTurn) turn.state()).continuation(), programs);
        assertThat(continuation.invocations())
            .extracting(RunContinuation.ProcessInvocation::processId)
            .containsExactly(parent.processId());
        assertThat(continuation.requireInvocation(0).continuation().resumePoint()).isEqualTo(ResumePoint.beforeElement(
                "end"));
    }

    @Test
    void committedWaitFactResumesFromExactSemanticCheckpoint() {
        DurableProcessRuntime program = loadProgram("turn.wait", waitFlow());
        CapturingStore first = new CapturingStore(Optional.of(startClaim(program, false)), true);
        assertThat(run(first, cache(program), new DurableRuntimeMetrics())).isTrue();
        DurableStore.TurnCommit waitingTurn = (DurableStore.TurnCommit) first.value;
        DurableStore.WaitCommit wait = (DurableStore.WaitCommit) waitingTurn.issuedOccurrences().get(0);
        DurableStore.WaitingTurn waiting = (DurableStore.WaitingTurn) waitingTurn.state();

        DurableStore.RunClaim resumed = new DurableStore.RunClaim(lease(), runProcess(program), waiting.continuation(),
                1L,
                List.of(
                        new DurableStore.WaitResult(wait.occurrence(), 1L, wait.processId(), wait.processInvocationId(),
                                wait.frontierId(), "approval", "approved", DurableStore.WaitResolution.COMPLETED,
                                new DurableStore.Envelope(program.valueSerializer().encodeWaitPayload(Map.of())), null,
                                NOW)), false, NOW.plus(LEASE_DURATION));
        CapturingStore second = new CapturingStore(Optional.of(resumed), true);

        assertThat(run(second, cache(program), new DurableRuntimeMetrics())).isTrue();
        assertThat(second.mutation).isEqualTo("commitTurn");
        DurableStore.TurnCommit completed = (DurableStore.TurnCommit) second.value;
        assertThat(completed.state()).isInstanceOf(DurableStore.SucceededTurn.class);
        assertThat(completed.consumedOccurrences()).containsExactly(wait.occurrence());
    }

    @Test
    void consumedPersistedOccurrencesMustHaveExactlyOneStoreResult() {
        UUID occurrenceId = UUID.fromString("00000000-0000-0000-0000-000000000103");
        OccurrenceKey consumed = new OccurrenceKey(BoundaryKind.WAIT, occurrenceId);
        MachineTurnResult turn = new MachineTurnResult(new FrontierStepResult.Completed(Map.of()), List.of(consumed));
        DurableStore.WaitResult result = waitResult(occurrenceId);

        List<DurableStore.OccurrenceKey> collected = new java.util.ArrayList<>();
        DurableTurnWorker.collectConsumedResults(turn, List.of(result), collected);
        assertThat(collected).containsExactly(result.occurrence());

        assertThatThrownBy(() -> DurableTurnWorker.collectConsumedResults(turn, List.of(), new java.util.ArrayList<>()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not supplied by the Store");
        assertThatThrownBy(() -> DurableTurnWorker.collectConsumedResults(turn, List.of(result, result),
                new java.util.ArrayList<>()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("more than once");
    }

    @Test
    void syntheticProcessReturnIsNotReportedAsAConsumedStoreOccurrence() {
        OccurrenceKey processReturn =
                new OccurrenceKey(BoundaryKind.PROCESS_CALL, UUID.fromString("00000000-0000-0000-0000-000000000104"));
        MachineTurnResult turn =
                new MachineTurnResult(new FrontierStepResult.Completed(Map.of()), List.of(processReturn));
        List<DurableStore.OccurrenceKey> collected = new java.util.ArrayList<>();

        DurableTurnWorker.collectConsumedResults(turn, List.of(), collected);

        assertThat(collected).isEmpty();
    }

    @Test
    void cancellationCacheEvictionAndFaultUseDistinctFencedTransitions() {
        DurableProcessRuntime program = loadProgram("turn.complete", completedFlow("turn.complete"));

        CapturingStore cancelled = new CapturingStore(Optional.of(startClaim(program, true)), true);
        assertThat(run(cancelled, cache(program), new DurableRuntimeMetrics())).isTrue();
        assertThat(cancelled.mutation).isEqualTo("commitRunCancelled");

        CapturingStore evicted = new CapturingStore(Optional.of(startClaim(program, false)), true);
        DurableProcessRuntimeCache disappearing = new DurableProcessRuntimeCache() {
            @Override
            public DurableProcessRuntime put(DurableProcessRuntime program) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<DurableProcessRuntime> get(UUID processId) {
                return Optional.empty();
            }

            @Override
            public Set<DurableProcessRuntime> snapshot() {
                return Set.of(program);
            }
        };
        assertThat(run(evicted, disappearing, new DurableRuntimeMetrics())).isTrue();
        assertThat(evicted.mutation).isEqualTo("releaseRunAfterCapabilityLoss");
        assertThat(evicted.reason).isNull();

        DurableStore.RunClaim corrupt = new DurableStore.RunClaim(lease(), runProcess(program),
                new DurableStore.Envelope("not-json".getBytes(StandardCharsets.UTF_8)), 0L, List.of(), false,
                NOW.plus(LEASE_DURATION));
        CapturingStore faulted = new CapturingStore(Optional.of(corrupt), true);
        DurableRuntimeMetrics metrics = new DurableRuntimeMetrics();
        assertThat(run(faulted, cache(program), metrics)).isTrue();
        assertThat(faulted.mutation).isEqualTo("releaseRunFault");
        assertThat(faulted.reason).isEqualTo(RunRetryCode.TURN_EXECUTION_FAULT);
        assertThat(faulted.delay).isEqualTo(FAULT_BACKOFF);
        assertThat(metrics.count(Operation.TURN, Outcome.FAULT)).isOne();
    }

    private boolean run(CapturingStore store, DurableProcessRuntimeCache cache, DurableRuntimeMetrics metrics) {
        return run(store, cache, metrics, 10_000);
    }

    private boolean run(CapturingStore store, DurableProcessRuntimeCache cache, DurableRuntimeMetrics metrics,
            int turnMaxSteps) {
        DurableStore proxy = store.proxy();
        DurableProcessRuntimeManager processManager = new DurableProcessRuntimeManager(proxy, cache,
                new DurableJavaProgramCompiler(JavaDiagnosticsConfig.defaults()),
                ProcessEngineConfig.tbbpmBuilder().classLoader(getClass().getClassLoader()).discoverPlugins(false).build(),
                DurableVersionDefinitionSource.empty());
        try (DurableLeaseRenewer renewer = new DurableLeaseRenewer(proxy, LEASE_DURATION, metrics)) {
            return new DurableTurnWorker(proxy, processManager, cache, DurableActionInvoker.unavailable(),
                    DurableWaitDescriptionProvider.defaults(),
                    () -> new WaitTokenIssuer.IssuedWaitToken(WAIT_TOKEN, WAIT_TOKEN_DIGEST),
                    new DurableTurnWorkerOptions("turn-worker", FAULT_BACKOFF, turnMaxSteps, 32), renewer, metrics)
                .runOnce();
        }
    }

    private DurableProcessRuntime loadProgram(String code, String xml) {
        byte[] definition = xml.getBytes(StandardCharsets.UTF_8);
        var model = TbbpmXmlParser.getInstance().parse(FlowSource.of(code, definition));
        var machinePlan = DurableCompilerTestSupport.lower(model);
        var program = new DurableJavaProgramCompiler(JavaDiagnosticsConfig.defaults())
            .compile(machinePlan, getClass().getClassLoader());
        ProcessRef.Version version = ProcessRef.version("test", code, "v1");
        String digest = ProcessDefinitionDigest.compute(ProcessModelType.TBBPM, code, definition);
        UUID processId = UUID.nameUUIDFromBytes((version + digest).getBytes(StandardCharsets.UTF_8));
        return new DurableProcessRuntime(processId, code, ProcessModelType.TBBPM, digest, machinePlan, program,
                new DurableValueSerializer(machinePlan), Map.of());
    }

    private InMemoryDurableProcessRuntimeCache cache(DurableProcessRuntime... programs) {
        InMemoryDurableProcessRuntimeCache cache = new InMemoryDurableProcessRuntimeCache();
        for (DurableProcessRuntime program : programs) {
            cache.put(program);
        }
        return cache;
    }

    private DurableStore.RunClaim startClaim(DurableProcessRuntime program, boolean cancelRequested) {
        return startClaim(program, cancelRequested, Map.of());
    }

    private DurableStore.RunClaim startClaim(DurableProcessRuntime program, boolean cancelRequested,
            Map<RunContinuation.ProcessCallSite, UUID> processCallTargets) {
        Function<UUID, DurableProcessRuntime> programResolver =
                processId -> processId.equals(program.processId()) ? program : null;
        byte[] snapshot = new RunContinuationCodec()
            .encode(RunContinuation.start(program.processId(), ContinuationSnapshot.start(Map.of()), processCallTargets),
                    programResolver);
        return new DurableStore.RunClaim(lease(), runProcess(program), new DurableStore.Envelope(snapshot), 0L,
                List.of(), cancelRequested, NOW.plus(LEASE_DURATION));
    }

    private static DurableStore.RunProcess runProcess(DurableProcessRuntime program) {
        ProcessRef.Version version = processVersion(program);
        return new DurableStore.RunProcess(program.processId(), version.namespace(), program.processCode(), version);
    }

    private static ProcessRef.Version processVersion(DurableProcessRuntime program) {
        return ProcessRef.version("test", program.processCode(), "v1");
    }

    private static RunContinuation decode(DurableStore.Envelope continuation, DurableProcessRuntimeCache programs) {
        return new RunContinuationCodec().decode(continuation.payload(), programs);
    }

    private DurableStore.RunLease lease() {
        return new DurableStore.RunLease(RUN_ID, LEASE_TOKEN);
    }

    private DurableStore.WaitResult waitResult(UUID occurrenceId) {
        return new DurableStore.WaitResult(new DurableStore.OccurrenceKey(DurableStore.OccurrenceKind.WAIT, occurrenceId),
                1L, UUID.fromString("00000000-0000-0000-0000-000000000105"), 0L, "root", "approval", "approved",
                DurableStore.WaitResolution.COMPLETED, new DurableStore.Envelope("{}".getBytes(StandardCharsets.UTF_8)),
                null, NOW);
    }

    private String completedFlow(String code) {
        return """
            <bpm code="%s">
              <start id="start" g="0,0,32,32"><transition to="end"/></start>
              <end id="end" g="100,0,32,32"/>
            </bpm>
            """
            .formatted(code);
    }

    private String waitFlow() {
        return """
            <bpm code="turn.wait">
              <start id="start" g="0,0,32,32"><transition to="approval"/></start>
              <waitEventTask id="approval" event="approved" g="60,0,100,40">
                <transition to="end"/>
              </waitEventTask>
              <end id="end" g="200,0,32,32"/>
            </bpm>
            """;
    }

    private String timerFlow() {
        return """
            <bpm code="turn.timer">
              <start id="start" g="0,0,32,32"><transition to="cooling"/></start>
              <timerTask id="cooling" duration="PT5M" g="60,0,100,40">
                <transition to="end"/>
              </timerTask>
              <end id="end" g="200,0,32,32"/>
            </bpm>
            """;
    }

    private String effectFlow() {
        return """
            <bpm code="turn.effect">
              <start id="start" g="0,0,32,32"><transition to="externalCall"/></start>
              <autoTask id="externalCall" g="60,0,100,40">
                <action type="java" execution="effect" class="%s" method="mustNotRun"/>
                <transition to="end"/>
              </autoTask>
              <end id="end" g="200,0,32,32"/>
            </bpm>
            """
            .formatted(WorkerActions.class.getName());
    }

    private String childFlow() {
        return """
            <bpm code="turn.child">
              <start id="start" g="0,0,32,32"><transition to="call"/></start>
              <bpmCall id="call" code="child.flow" version="v1" g="60,0,100,40">
                <transition to="end"/>
              </bpmCall>
              <end id="end" g="200,0,32,32"/>
            </bpm>
            """;
    }

    private String childWaitFlow() {
        return """
            <bpm code="child.flow">
              <start id="start" g="0,0,32,32"><transition to="approval"/></start>
              <waitEventTask id="approval" event="approved" g="60,0,100,40">
                <transition to="end"/>
              </waitEventTask>
              <end id="end" g="200,0,32,32"/>
            </bpm>
            """;
    }

    private static final class CapturingStore {
        private final Optional<DurableStore.RunClaim> claim;
        private final boolean mutationApplied;
        private boolean claimed;
        private DurableStore.RunClaimRequest claimRequest;
        private String mutation;
        private Object value;
        private Duration delay;
        private RunRetryCode reason;
        private final Map<Object, DurableStore.StoredProcess> processes = new LinkedHashMap<>();

        private CapturingStore(Optional<DurableStore.RunClaim> claim, boolean mutationApplied) {
            this.claim = claim;
            this.mutationApplied = mutationApplied;
        }

        private void addProcess(DurableProcessRuntime process, String definition) {
            DurableStore.StoredProcess stored = new DurableStore.StoredProcess(process.processId(),
                    process.processCode(), ProcessModelType.TBBPM, definition.getBytes(StandardCharsets.UTF_8),
                    process.definitionDigest(), NOW);
            processes.put(process.processId(), stored);
        }

        private DurableStore proxy() {
            InvocationHandler handler =
                    (instance, method, arguments) -> switch (method.getName()) {
                case "registerProcess" -> register((DurableStore.ProcessRegistration) arguments[0]);
                case "findProcess" -> Optional.ofNullable(processes.get(arguments[0]));
                case "claimRun" -> claim((DurableStore.RunClaimRequest) arguments[0]);
                case "renewRunLeases" -> arguments[0];
                case "commitTurn" -> mutate(method.getName(), arguments[1], null, null);
                case "commitRunCancelled" -> mutate(method.getName(), null, null, null);
                case "releaseRunAfterCapabilityLoss" -> mutate(method.getName(), null, arguments[1], null);
                case "releaseRunFault" -> mutate(method.getName(), null, arguments[1], arguments[2]);
                case "toString" -> "CapturingTurnStore";
                default -> throw new AssertionError("Unexpected Store call: " + method.getName());
            };
            return (DurableStore) Proxy.newProxyInstance(DurableStore.class.getClassLoader(),
                    new Class<?>[] {DurableStore.class}, handler);
        }

        private DurableStore.StoredProcess register(DurableStore.ProcessRegistration registration) {
            DurableStore.StoredProcess existing = processes.get(registration.processId());
            if (existing != null) {
                return existing;
            }
            DurableStore.StoredProcess stored = new DurableStore.StoredProcess(registration.processId(),
                    registration.processCode(), registration.modelType(), registration.definitionBytes(),
                    registration.definitionDigest(), NOW);
            processes.put(registration.processId(), stored);
            return stored;
        }

        private Optional<DurableStore.RunClaim> claim(DurableStore.RunClaimRequest request) {
            claimRequest = request;
            if (claimed) {
                return Optional.empty();
            }
            claimed = true;
            return claim;
        }

        private boolean mutate(String operation, Object value, Object delay, Object reason) {
            if (mutation != null) {
                throw new AssertionError("Run claim completed more than once");
            }
            mutation = operation;
            this.value = value;
            this.delay = (Duration) delay;
            this.reason = (RunRetryCode) reason;
            return mutationApplied;
        }
    }

    public static final class WorkerActions {
        public void mustNotRun() {
            throw new AssertionError("Effect must not execute in the Turn lane");
        }
    }
}
