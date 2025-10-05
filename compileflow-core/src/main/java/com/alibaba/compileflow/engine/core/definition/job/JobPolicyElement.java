package com.alibaba.compileflow.engine.core.definition.job;

/**
 * @author yusu
 */
public interface JobPolicyElement extends RetryElement, FailureElement {

    /**
     * ISO-8601 timeout string, e.g. PT2S
     */
    String getTimeout();

}
