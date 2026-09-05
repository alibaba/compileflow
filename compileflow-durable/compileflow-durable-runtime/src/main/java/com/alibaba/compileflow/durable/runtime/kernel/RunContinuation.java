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
package com.alibaba.compileflow.durable.runtime.kernel;

import com.alibaba.compileflow.durable.api.validation.DurableNumbers;
import com.alibaba.compileflow.durable.api.validation.DurableIdentifiers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Persisted same-Run execution state and exact Process-call targets.
 *
 * @author yusu
 */
public record RunContinuation(long nextInvocationId, List<ProcessInvocation> invocations,
        Map<ProcessCallSite, UUID> processCallTargets) {
    public static final int MAX_INVOCATIONS = 1_024;
    public static final int MAX_PROCESS_CALL_TARGETS = 4_096;

    public RunContinuation {
        nextInvocationId = DurableNumbers.requireNonNegative(nextInvocationId, "nextInvocationId");
        if (nextInvocationId == 0) {
            throw new IllegalArgumentException("nextInvocationId must be positive");
        }
        invocations = List.copyOf(Objects.requireNonNull(invocations, "invocations"));
        processCallTargets = Map.copyOf(Objects.requireNonNull(processCallTargets, "processCallTargets"));
        if (processCallTargets.size() > MAX_PROCESS_CALL_TARGETS) {
            throw new IllegalArgumentException("Run continuation has too many Process call bindings");
        }
        if (invocations.isEmpty() || invocations.size() > MAX_INVOCATIONS) {
            throw new IllegalArgumentException(
                    "Run continuation must contain 1.." + MAX_INVOCATIONS + " active Process invocations");
        }
        Map<Long, ProcessInvocation> byId = new HashMap<>();
        boolean rootFound = false;
        for (ProcessInvocation invocation : invocations) {
            ProcessInvocation value = Objects.requireNonNull(invocation, "invocations contains null");
            if (byId.putIfAbsent(value.invocationId(), value) != null || value.invocationId() >= nextInvocationId) {
                throw new IllegalArgumentException("Process invocation identities must be unique and allocated");
            }
            rootFound |= value.invocationId() == 0;
        }
        if (!rootFound) {
            throw new IllegalArgumentException("Run continuation must contain the root Process invocation");
        }
        validateReturnAddresses(invocations, byId, processCallTargets);
    }

    private static void validateReturnAddresses(List<ProcessInvocation> invocations, Map<Long, ProcessInvocation> byId,
            Map<ProcessCallSite, UUID> processCallTargets) {
        Set<ReturnAddress> activeCalls = new HashSet<>();
        for (ProcessInvocation invocation : invocations) {
            ReturnAddress address = invocation.returnAddress();
            if (address == null) {
                continue;
            }
            ProcessInvocation parent = byId.get(address.invocationId());
            if (parent == null || parent.invocationId() >= invocation.invocationId()) {
                throw new IllegalArgumentException("Process return address must target an ancestor invocation");
            }
            FrontierSnapshot parentFrontier = parent
                .continuation()
                .frontiers()
                .stream()
                .filter(frontier -> frontier.frontierId().equals(address.frontierId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Process return address targets an absent frontier"));
            if (!parentFrontier.resumePoint().isAfterElement()
                    || !address.elementId().equals(parentFrontier.resumePoint().elementId())) {
                throw new IllegalArgumentException("Process return address does not match the parent call checkpoint");
            }
            UUID target = processCallTargets.get(new ProcessCallSite(parent.processId(), address.elementId()));
            if (!invocation.processId().equals(target)) {
                throw new IllegalArgumentException("Process invocation does not match its exact call target");
            }
            if (!activeCalls.add(address)) {
                throw new IllegalArgumentException("Process return address has more than one active child invocation");
            }
        }
    }

    public static RunContinuation start(UUID rootProcessId, ContinuationSnapshot continuation,
            Map<ProcessCallSite, UUID> processCallTargets) {
        return new RunContinuation(1, List.of(new ProcessInvocation(0, rootProcessId, continuation, null)),
                processCallTargets);
    }

    public ProcessInvocation requireInvocation(long invocationId) {
        return invocations
            .stream()
            .filter(invocation -> invocation.invocationId() == invocationId)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Process invocation is absent: " + invocationId));
    }

    public boolean hasRunnableInvocation(int maxActiveIterations) {
        return invocations
            .stream()
            .anyMatch(invocation -> invocation.continuation().hasRunnableFrontier(maxActiveIterations));
    }

    public RunContinuation replace(ProcessInvocation invocation) {
        ProcessInvocation replacement = Objects.requireNonNull(invocation, "invocation");
        List<ProcessInvocation> updated = new ArrayList<>(invocations.size());
        boolean found = false;
        for (ProcessInvocation current : invocations) {
            if (current.invocationId() == replacement.invocationId()) {
                updated.add(replacement);
                found = true;
            } else {
                updated.add(current);
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Process invocation is absent: " + replacement.invocationId());
        }
        return new RunContinuation(nextInvocationId, updated, processCallTargets);
    }

    public RunContinuation call(ProcessInvocation caller, UUID calledProcessId, ContinuationSnapshot calledProcess,
            FrontierId callerFrontierId, String elementId) {
        if (invocations.size() >= MAX_INVOCATIONS) {
            throw new IllegalStateException("Run has too many active Process invocations");
        }
        ProcessInvocation updatedCaller = Objects.requireNonNull(caller, "caller");
        ProcessInvocation currentCaller = requireInvocation(updatedCaller.invocationId());
        if (!currentCaller.processId().equals(updatedCaller.processId())
                || !Objects.equals(currentCaller.returnAddress(), updatedCaller.returnAddress())) {
            throw new IllegalArgumentException("Process call caller identity does not match the continuation");
        }
        FrontierId frontier = Objects.requireNonNull(callerFrontierId, "callerFrontierId");
        String callElement = DurableIdentifiers.requireIdentity(elementId, "elementId", 128);
        FrontierSnapshot callerFrontier = updatedCaller
            .continuation()
            .frontiers()
            .stream()
            .filter(candidate -> candidate.frontierId().equals(frontier))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Process call caller frontier is absent"));
        if (!callerFrontier.resumePoint().isAfterElement()
                || !callElement.equals(callerFrontier.resumePoint().elementId())) {
            throw new IllegalArgumentException("Process call caller frontier is not at its call site");
        }
        long calledInvocationId = nextInvocationId;
        ProcessInvocation calledInvocation = new ProcessInvocation(calledInvocationId, calledProcessId, calledProcess,
                new ReturnAddress(updatedCaller.invocationId(), frontier, callElement));
        List<ProcessInvocation> updated = new ArrayList<>(invocations.size() + 1);
        for (ProcessInvocation current : invocations) {
            updated.add(current.invocationId() == updatedCaller.invocationId() ? updatedCaller : current);
        }
        updated.add(calledInvocation);
        return new RunContinuation(Math.addExact(nextInvocationId, 1), updated, processCallTargets);
    }

    public RunContinuation remove(long invocationId) {
        if (invocationId == 0) {
            throw new IllegalArgumentException("Root Process invocation cannot be removed");
        }
        List<ProcessInvocation> updated =
                invocations
            .stream()
            .filter(invocation -> invocation.invocationId() != invocationId)
            .toList();
        if (updated.size() == invocations.size()) {
            throw new IllegalArgumentException("Process invocation is absent: " + invocationId);
        }
        return new RunContinuation(nextInvocationId, updated, processCallTargets);
    }

    public UUID requireProcessCallTarget(UUID callerProcessId, String callSiteId) {
        ProcessCallSite site = new ProcessCallSite(callerProcessId, callSiteId);
        UUID calledProcessId = processCallTargets.get(site);
        if (calledProcessId == null) {
            throw new IllegalStateException("Resolved Durable Process call is unavailable: " + site);
        }
        return calledProcessId;
    }

    /**
     * One exact call edge in the Run's persisted Process graph.
     */
    public record ProcessCallSite(UUID callerProcessId, String callSiteId) {
        public ProcessCallSite {
            callerProcessId = Objects.requireNonNull(callerProcessId, "callerProcessId");
            callSiteId = DurableIdentifiers.requireIdentity(callSiteId, "callSiteId", 128);
        }
    }

    public record ProcessInvocation(long invocationId, UUID processId, ContinuationSnapshot continuation,
            ReturnAddress returnAddress) {
        public ProcessInvocation {
            invocationId = DurableNumbers.requireNonNegative(invocationId, "invocationId");
            processId = Objects.requireNonNull(processId, "processId");
            continuation = Objects.requireNonNull(continuation, "continuation");
            if ((invocationId == 0) != (returnAddress == null)) {
                throw new IllegalArgumentException("Only the root Process invocation omits a return address");
            }
        }

        public ProcessInvocation withContinuation(ContinuationSnapshot next) {
            return new ProcessInvocation(invocationId, processId, next, returnAddress);
        }
    }

    public record ReturnAddress(long invocationId, FrontierId frontierId, String elementId) {
        public ReturnAddress {
            invocationId = DurableNumbers.requireNonNegative(invocationId, "invocationId");
            frontierId = Objects.requireNonNull(frontierId, "frontierId");
            elementId = DurableIdentifiers.requireIdentity(elementId, "elementId", 128);
        }
    }
}
