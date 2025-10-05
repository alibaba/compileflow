package com.alibaba.compileflow.engine.core.runtime.executor;

import java.util.*;

/**
 * @author yusu
 */
public class GatewayExecutor {

    private GatewayExecutor() {
    }

    public static ExclusiveBranches exclusive(String gatewayId, Map<String, Object> ctx) {
        return new ExclusiveBranches(gatewayId, ctx);
    }

    public static ParallelBranches parallel(String gatewayId, Map<String, Object> ctx) {
        return new ParallelBranches(gatewayId, ctx);
    }

    public static InclusiveBranches inclusive(String gatewayId, Map<String, Object> ctx) {
        return new InclusiveBranches(gatewayId, ctx);
    }

    @FunctionalInterface
    public interface ThrowingConsumer<T> {
        void accept(T t) throws Exception;
    }

    public static class ParallelBranches extends GatewayBranches {
        public ParallelBranches(String gatewayId, Map<String, Object> ctx) {
            super(gatewayId, ctx);
        }

        public ParallelBranches branch(ThrowingConsumer<Map<String, Object>> branch) {
            runs.add(() -> {
                try {
                    branch.accept(roCtx);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            return this;
        }
    }

    public static class ExclusiveBranches extends GatewayBranches {
        public ExclusiveBranches(String gatewayId, Map<String, Object> ctx) {
            super(gatewayId, ctx);
        }

        public ExclusiveBranches when(boolean cond, ThrowingConsumer<Map<String, Object>> branch) {
            if (cond) {
                runs.add(() -> {
                    try {
                        branch.accept(roCtx);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            return this;
        }

        public ExclusiveBranches when(Boolean cond, ThrowingConsumer<Map<String, Object>> branch) {
            if (Boolean.TRUE.equals(cond)) {
                runs.add(() -> {
                    try {
                        branch.accept(roCtx);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            return this;
        }

        public ExclusiveBranches otherwise(ThrowingConsumer<Map<String, Object>> branch) {
            if (runs.isEmpty()) {
                runs.add(() -> {
                    try {
                        branch.accept(roCtx);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            return this;
        }
    }

    public static class InclusiveBranches extends GatewayBranches {
        public InclusiveBranches(String gatewayId, Map<String, Object> ctx) {
            super(gatewayId, ctx);
        }

        public InclusiveBranches when(boolean cond, ThrowingConsumer<Map<String, Object>> branch) {
            if (cond) {
                runs.add(() -> {
                    try {
                        branch.accept(roCtx);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            return this;
        }

        public InclusiveBranches when(Boolean cond, ThrowingConsumer<Map<String, Object>> branch) {
            if (Boolean.TRUE.equals(cond)) {
                runs.add(() -> {
                    try {
                        branch.accept(roCtx);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            return this;
        }

        public InclusiveBranches otherwise(ThrowingConsumer<Map<String, Object>> branch) {
            if (runs.isEmpty()) {
                runs.add(() -> {
                    try {
                        branch.accept(roCtx);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            return this;
        }
    }

    public static abstract class GatewayBranches {

        protected final Map<String, Object> roCtx;
        protected final List<Runnable> runs = new ArrayList<>();
        private final String gatewayId;

        public GatewayBranches(String gatewayId, Map<String, Object> ctx) {
            this.gatewayId = gatewayId;
            this.roCtx = Collections.unmodifiableMap(new HashMap<>(ctx));
        }

        public void run() throws Exception {
            if (runs.size() == 1) {
                runs.get(0).run();
            } else if (runs.size() > 1) {
                List<ParallelBranch> branches = new ArrayList<>(runs.size());
                for (int i = 0; i < runs.size(); i++) {
                    String name = gatewayId + "#" + i;
                    branches.add(ParallelBranch.of(name, runs.get(i)));
                }
                ParallelExecutor.execute(branches);
            }
        }
    }

}
