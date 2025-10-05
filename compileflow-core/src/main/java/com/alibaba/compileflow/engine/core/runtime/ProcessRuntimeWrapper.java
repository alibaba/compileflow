package com.alibaba.compileflow.engine.core.runtime;

/**
 * A wrapper for a compiled {@link ProcessRuntime}, containing essential metadata
 * about the specific compiled version.
 *
 * @author yusu
 */
public class ProcessRuntimeWrapper {
    /**
     * The compiled process runtime instance.
     */
    private final ProcessRuntime runtime;

    /**
     * The process code (e.g., "my.process") associated with this runtime.
     */
    private final String processCode;

    /**
     * A unique digest (hash) of the process source content and ClassLoader,
     * serving as the definitive identifier for a compiled version.
     */
    private final String digest;

    /**
     * The timestamp of when this runtime was compiled, for diagnostics.
     */
    private final long compiledTime;

    public ProcessRuntimeWrapper(ProcessRuntime runtime, String processCode, String digest) {
        this.runtime = runtime;
        this.processCode = processCode;
        this.digest = digest;
        this.compiledTime = System.currentTimeMillis();
    }

    public ProcessRuntime getRuntime() {
        return runtime;
    }

    public String getProcessCode() {
        return processCode;
    }

    public String getDigest() {
        return digest;
    }

    public long getCompiledTime() {
        return compiledTime;
    }

}
