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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RunContinuationTest {
    private static final UUID ROOT_PROCESS = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CHILD_PROCESS = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final RunContinuation.ProcessCallSite CALL_SITE =
            new RunContinuation.ProcessCallSite(ROOT_PROCESS, "call");

    @Test
    void callAppendsAChildAndPreservesTheCallerCheckpoint() {
        RunContinuation.ProcessInvocation caller =
                caller(ContinuationSnapshot.afterElement("call", Map.of(), List.of()));
        RunContinuation continuation = new RunContinuation(1, List.of(caller), Map.of(CALL_SITE, CHILD_PROCESS));

        RunContinuation called =
                continuation.call(caller, CHILD_PROCESS, ContinuationSnapshot.start(Map.of()), FrontierId.ROOT, "call");

        assertThat(called.invocations()).hasSize(2);
        assertThat(called.invocations().get(0)).isEqualTo(caller);
        assertThat(called.invocations().get(1).invocationId()).isOne();
        assertThat(called.invocations().get(1).processId()).isEqualTo(CHILD_PROCESS);
        assertThat(called.invocations().get(1).returnAddress())
            .isEqualTo(new RunContinuation.ReturnAddress(0, FrontierId.ROOT, "call"));
    }

    @Test
    void callRejectsACallerWithDifferentProcessIdentity() {
        RunContinuation.ProcessInvocation caller =
                caller(ContinuationSnapshot.afterElement("call", Map.of(), List.of()));
        RunContinuation continuation = new RunContinuation(1, List.of(caller), Map.of(CALL_SITE, CHILD_PROCESS));
        RunContinuation.ProcessInvocation forged = new RunContinuation.ProcessInvocation(0,
                UUID.fromString("00000000-0000-0000-0000-000000000003"), caller.continuation(), null);

        assertThatThrownBy(() -> continuation.call(forged, CHILD_PROCESS, ContinuationSnapshot.start(Map.of()),
                FrontierId.ROOT, "call"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Process call caller identity does not match the continuation");
    }

    @Test
    void callRejectsAFrontierThatIsNotTheCallCheckpoint() {
        RunContinuation.ProcessInvocation caller =
                caller(ContinuationSnapshot.beforeElement("call", Map.of(), List.of()));
        RunContinuation continuation = new RunContinuation(1, List.of(caller), Map.of(CALL_SITE, CHILD_PROCESS));

        assertThatThrownBy(() -> continuation.call(caller, CHILD_PROCESS, ContinuationSnapshot.start(Map.of()),
                FrontierId.ROOT, "call"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Process call caller frontier is not at its call site");
    }

    @Test
    void rejectsMalformedPersistedProcessCallAncestry() {
        RunContinuation.ProcessInvocation parent =
                caller(ContinuationSnapshot.afterElement("call", Map.of(), List.of()));
        RunContinuation.ProcessInvocation child = new RunContinuation.ProcessInvocation(1, CHILD_PROCESS,
                ContinuationSnapshot.start(Map.of()), new RunContinuation.ReturnAddress(0, FrontierId.ROOT, "call"));

        assertThatThrownBy(() -> new RunContinuation(2, List.of(parent, child), Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Process invocation does not match its exact call target");
        assertThatThrownBy(() -> new RunContinuation(2,
                List.of(parent,
                        new RunContinuation.ProcessInvocation(1, CHILD_PROCESS, ContinuationSnapshot.start(Map.of()),
                                new RunContinuation.ReturnAddress(1, FrontierId.ROOT, "call"))),
                Map.of(CALL_SITE, CHILD_PROCESS)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Process return address must target an ancestor invocation");
    }

    @Test
    void rejectsMalformedPersistedProcessCallCheckpoint() {
        RunContinuation.ProcessInvocation child = new RunContinuation.ProcessInvocation(1, CHILD_PROCESS,
                ContinuationSnapshot.start(Map.of()), new RunContinuation.ReturnAddress(0, FrontierId.ROOT, "call"));

        assertThatThrownBy(() -> new RunContinuation(2,
                List.of(caller(ContinuationSnapshot.afterElement("other", Map.of(), List.of())), child),
                Map.of(CALL_SITE, CHILD_PROCESS)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Process return address does not match the parent call checkpoint");
        assertThatThrownBy(() -> new RunContinuation(2,
                List.of(caller(ContinuationSnapshot.afterElement("call", Map.of(), List.of())),
                        new RunContinuation.ProcessInvocation(1, CHILD_PROCESS, ContinuationSnapshot.start(Map.of()),
                                new RunContinuation.ReturnAddress(0, new FrontierId("absent"), "call"))),
                Map.of(CALL_SITE, CHILD_PROCESS)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Process return address targets an absent frontier");
    }

    @Test
    void rejectsMoreThanOneActiveChildAtTheSameCallSite() {
        RunContinuation.ReturnAddress address = new RunContinuation.ReturnAddress(0, FrontierId.ROOT, "call");
        RunContinuation.ProcessInvocation first =
                new RunContinuation.ProcessInvocation(1, CHILD_PROCESS, ContinuationSnapshot.start(Map.of()), address);
        RunContinuation.ProcessInvocation second =
                new RunContinuation.ProcessInvocation(2, CHILD_PROCESS, ContinuationSnapshot.start(Map.of()), address);

        assertThatThrownBy(() -> new RunContinuation(3,
                List.of(caller(ContinuationSnapshot.afterElement("call", Map.of(), List.of())), first, second),
                Map.of(CALL_SITE, CHILD_PROCESS)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Process return address has more than one active child invocation");
    }

    private static RunContinuation.ProcessInvocation caller(ContinuationSnapshot continuation) {
        return new RunContinuation.ProcessInvocation(0, ROOT_PROCESS, continuation, null);
    }
}
