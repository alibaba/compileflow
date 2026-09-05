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
package com.alibaba.compileflow.engine.core.runtime.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoopSemanticsTest {
    @Test
    void snapshotsIterableWithoutRejectingNullElements() {
        List<String> source = new ArrayList<>(List.of("first", "second"));
        source.add(null);

        List<String> snapshot = LoopSemantics.snapshot(source, "loop", String.class);
        source.clear();

        assertThat(snapshot).containsExactly("first", "second", null);
        assertThatThrownBy(() -> snapshot.add("third")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void snapshotsPrimitiveArraysInIndexOrder() {
        List<Integer> snapshot = LoopSemantics.snapshot(new int[] {3, 1, 2}, "loop", Integer.class);

        assertThat(snapshot).containsExactly(3, 1, 2);
    }

    @Test
    void rejectsNullAndNonIterableInputsWithLoopContext() {
        assertThatThrownBy(() -> LoopSemantics.snapshot(null, "orders", Object.class))
            .isInstanceOfSatisfying(CompileFlowException.class, failure -> {
                assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.CF_EXEC_008);
                assertThat(failure.getContext()).containsEntry("loopId", "orders");
            });

        assertThatThrownBy(() -> LoopSemantics.snapshot(42, "orders", Object.class))
            .isInstanceOfSatisfying(CompileFlowException.class, failure -> assertThat(failure.getContext())
                .containsEntry("sourceType", Integer.class.getName()));
    }

    @Test
    void rejectsAnItemThatViolatesTheDeclaredLoopType() {
        assertThatThrownBy(() -> LoopSemantics.snapshot(List.of("valid", 42), "orders", String.class))
            .isInstanceOfSatisfying(CompileFlowException.class, failure -> {
                assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.CF_EXEC_008);
                assertThat(failure.getContext())
                    .containsEntry("loopId", "orders")
                    .containsEntry("itemIndex", 1)
                    .containsEntry("expectedItemType", String.class.getName())
                    .containsEntry("sourceType", Integer.class.getName());
            });
    }

    @Test
    void rejectsIterationsAtTheDeclaredLimitWithLoopContext() {
        LoopSemantics.requireIterationAllowed("retry", 1, 2);

        assertThatThrownBy(() -> LoopSemantics.requireIterationAllowed("retry", 2, 2))
            .isInstanceOfSatisfying(CompileFlowException.class, failure -> {
                assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.CF_EXEC_008);
                assertThat(failure.getContext())
                    .containsEntry("loopId", "retry")
                    .containsEntry("completedIterations", 2)
                    .containsEntry("maxIterations", 2);
            });
    }

    @Test
    void rejectsInvalidIterationLimitArguments() {
        assertThatThrownBy(() -> LoopSemantics.requireIterationAllowed("retry", 0, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxIterations");
        assertThatThrownBy(() -> LoopSemantics.requireIterationAllowed("retry", -1, 2))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("completedIterations");
    }
}
