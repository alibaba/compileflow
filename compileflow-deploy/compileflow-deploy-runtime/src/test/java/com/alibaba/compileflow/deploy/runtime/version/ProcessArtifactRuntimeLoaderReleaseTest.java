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
package com.alibaba.compileflow.deploy.runtime.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.engine.core.runtime.ownership.ProcessRuntimeOwnership;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessArtifactRuntimeLoaderReleaseTest {
    @Mock
    private ProcessRuntimeOwnership ownership;
    private ProcessArtifactRuntimeLoader loader;

    @BeforeEach
    void setUp() {
        loader = new ProcessArtifactRuntimeLoader(ownership, ignored -> java.util.List.of(), ignored -> null,
                new com.alibaba.compileflow.deploy.runtime.observability.DeploymentRuntimeMetrics());
    }

    @Test
    void releasedOwnerCannotPrepareARuntimeRetainedByAnotherOwner() {
        ProcessRef.Version ref = ProcessRef.version("default", "test.release", "v1");
        ProcessDefinition.Inline definition = ProcessDefinition.inline(ProcessModelType.TBBPM, ref.code(), "<flow/>");
        loader.load(new ProcessArtifact(ref, definition, ProcessArtifactDigest.compute(definition, java.util.Map.of())));
        when(ownership.releaseOwned(any(String.class), any(ProcessRef.Version.class)))
            .thenReturn(ProcessRuntimeOwnership.ReleaseOutcome.RETAINED);

        loader.release(ref.namespace(), ref.code(), ref.version());

        assertThatThrownBy(() -> loader.prepare(ref)).hasMessageContaining("not installed");
    }

    @Test
    void mapsOwnerAwareReleaseOutcome() {
        when(ownership.releaseOwned(any(String.class), any(ProcessRef.Version.class)))
            .thenReturn(ProcessRuntimeOwnership.ReleaseOutcome.RETAINED);

        assertThat(loader.release("default", "test.release", "v1"))
            .isEqualTo(ProcessArtifactRuntimeLoader.ReleaseResult.RETAINED);
    }

    @Test
    void reportsNotOwnedWithoutRemovingAnotherOwner() {
        when(ownership.releaseOwned(any(String.class), any(ProcessRef.Version.class)))
            .thenReturn(ProcessRuntimeOwnership.ReleaseOutcome.NOT_OWNED);

        assertThat(loader.release("default", "test.nonexistent", "v999"))
            .isEqualTo(ProcessArtifactRuntimeLoader.ReleaseResult.NOT_OWNED);
    }

    @Test
    void rejectsBlankInputsWithoutCallingTheEngine() {
        assertThatThrownBy(() -> loader.release("default", "", "v1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> loader.release("default", "test.flow", "")).isInstanceOf(
                IllegalArgumentException.class);
        assertThatThrownBy(() -> loader.release("default", null, "v1")).isInstanceOf(NullPointerException.class);
        verifyNoInteractions(ownership);
    }

    @Test
    void convertsEngineFailureIntoAnExplicitRetryableResult() {
        doThrow(new RuntimeException("engine cache error"))
            .when(ownership)
            .releaseOwned(any(String.class), any(ProcessRef.Version.class));

        assertThat(loader.release("default", "test.robust", "v1")).isEqualTo(
                ProcessArtifactRuntimeLoader.ReleaseResult.FAILED);
    }

    @Test
    void rejectsNullNamespaceBeforeRelease() {
        assertThatThrownBy(() -> loader.release(null, "test.ns", "v1"))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("namespace");
        verifyNoInteractions(ownership);
    }

    @Test
    void repeatedReleaseRemainsIdempotentAtTheOwnershipBoundary() {
        when(ownership.releaseOwned(any(String.class), any(ProcessRef.Version.class)))
            .thenReturn(ProcessRuntimeOwnership.ReleaseOutcome.REMOVED, ProcessRuntimeOwnership.ReleaseOutcome.NOT_OWNED);

        assertThat(loader.release("default", "test.multi", "v1")).isEqualTo(
                ProcessArtifactRuntimeLoader.ReleaseResult.REMOVED);
        assertThat(loader.release("default", "test.multi", "v1"))
            .isEqualTo(ProcessArtifactRuntimeLoader.ReleaseResult.NOT_OWNED);
        verify(ownership, times(2)).releaseOwned(any(String.class), any(ProcessRef.Version.class));
    }
}
