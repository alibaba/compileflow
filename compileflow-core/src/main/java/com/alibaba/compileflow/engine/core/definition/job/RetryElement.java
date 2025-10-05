package com.alibaba.compileflow.engine.core.definition.job;

import com.alibaba.compileflow.engine.core.definition.Element;

/**
 * @author yusu
 */
public interface RetryElement extends Element {

    /**
     * BPM retry spec, e.g. R3/PT0.2S
     */
    String getRetry();

    /**
     * Retry policy identifier, e.g. never/transient/always or bean name
     */
    String getRetryOn();

}
