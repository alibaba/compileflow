package com.alibaba.compileflow.engine.core.runtime.executor;

/**
 * Represents a single branch of execution in a parallel gateway.
 * It encapsulates the task logic (Runnable) along with its configuration.
 *
 * @author yusu
 */
public class ParallelBranch {

    private final String name;
    private final Runnable task;

    private ParallelBranch(String name, Runnable task) {
        this.name = name;
        this.task = task;
    }

    public static ParallelBranch of(String name, Runnable task) {
        return new ParallelBranch(name, task);
    }

    public String getName() {
        return name;
    }

    public Runnable getTask() {
        return task;
    }

}
