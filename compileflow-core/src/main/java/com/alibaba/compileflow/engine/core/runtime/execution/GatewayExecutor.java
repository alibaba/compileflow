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

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.ProcessIdentifiers;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Executes gateway branch selection and parallel branch dispatch.
 *
 * @author yusu
 */
public final class GatewayExecutor {
    private GatewayExecutor() {
    }

    public static <T> ParallelBranches<T> parallel(String gatewayId) {
        return new ParallelBranches<>(gatewayId);
    }

    public static <T> InclusiveBranches<T> inclusive(String gatewayId) {
        return new InclusiveBranches<>(gatewayId);
    }

    public static CompileFlowException noBranchMatched(String gatewayId) {
        CompileFlowException failure =
                new CompileFlowException(ErrorCode.CF_EXEC_008, "No outgoing branch matched gateway '" + gatewayId + "'");
        failure.withContext("gatewayId", gatewayId);
        return failure;
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    public record BranchId(String gatewayId, int ordinal, String targetId) {
        public BranchId {
            gatewayId = ProcessIdentifiers.requireNodeId(gatewayId);
            if (ordinal < 0) {
                throw new IllegalArgumentException("branch ordinal must not be negative");
            }
            targetId = ProcessIdentifiers.requireNodeId(targetId);
        }

        @Override
        public String toString() {
            return gatewayId + "#" + ordinal + "->" + targetId;
        }
    }

    public record BranchResult<T>(BranchId branchId, T value) {
        public BranchResult {
            Objects.requireNonNull(branchId, "branchId");
        }
    }

    public static final class ParallelBranches<T> extends GatewayBranches<T> {
        private ParallelBranches(String gatewayId) {
            super(gatewayId);
        }

        public ParallelBranches<T> branch(int ordinal, String targetId, ThrowingSupplier<T> branch) {
            addBranch(ordinal, targetId, branch);
            return this;
        }
    }

    public static final class InclusiveBranches<T> extends GatewayBranches<T> {
        private InclusiveBranches(String gatewayId) {
            super(gatewayId);
        }

        public InclusiveBranches<T> when(boolean cond, int ordinal, String targetId, ThrowingSupplier<T> branch) {
            if (cond) {
                addBranch(ordinal, targetId, branch);
            }
            return this;
        }

        public InclusiveBranches<T> when(Boolean cond, int ordinal, String targetId, ThrowingSupplier<T> branch) {
            if (Boolean.TRUE.equals(cond)) {
                addBranch(ordinal, targetId, branch);
            }
            return this;
        }

        public InclusiveBranches<T> otherwise(int ordinal, String targetId, ThrowingSupplier<T> branch) {
            if (!hasBranches()) {
                addBranch(ordinal, targetId, branch);
            }
            return this;
        }
    }

    public abstract static class GatewayBranches<T> {
        private final List<RegisteredBranch<T>> branches = new ArrayList<>();
        private final Set<Integer> ordinals = new LinkedHashSet<>();
        private final String gatewayId;

        private GatewayBranches(String gatewayId) {
            this.gatewayId = ProcessIdentifiers.requireNodeId(gatewayId);
        }

        protected final void addBranch(int ordinal, String targetId, ThrowingSupplier<T> branch) {
            if (ordinal < 0) {
                throw new IllegalArgumentException("branch ordinal must not be negative");
            }
            if (!ordinals.add(ordinal)) {
                throw new IllegalArgumentException(
                        "Duplicate branch ordinal " + ordinal + " for gateway '" + gatewayId + "'");
            }
            branches.add(
                    new RegisteredBranch<>(new BranchId(gatewayId, ordinal, targetId),
                            Objects.requireNonNull(branch, "branch")));
        }

        protected final boolean hasBranches() {
            return !branches.isEmpty();
        }

        public List<BranchResult<T>> run() throws Exception {
            if (branches.isEmpty()) {
                throw noBranchMatched(gatewayId);
            }
            if (branches.size() == 1) {
                RegisteredBranch<T> branch = branches.get(0);
                return List.of(new BranchResult<>(branch.id(), branch.task().get()));
            }

            List<ParallelTask<BranchResult<T>>> tasks = new ArrayList<>(branches.size());
            for (RegisteredBranch<T> branch : branches) {
                tasks.add(ParallelTask.of(branch.id().toString(), () -> new BranchResult<>(branch.id(),
                        branch.task().get())));
            }
            return ParallelExecutor.execute(tasks);
        }
    }

    private record RegisteredBranch<T>(BranchId id, ThrowingSupplier<T> task) {}
}
