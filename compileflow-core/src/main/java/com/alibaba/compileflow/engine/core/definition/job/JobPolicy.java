package com.alibaba.compileflow.engine.core.definition.job;

import com.alibaba.compileflow.engine.core.definition.BaseElement;

/**
 * JobPolicy strictly following ISO-8601 duration semantics.
 * - timeout: ISO-8601 only, e.g. PT2S, PT0.005S
 * - retry:   R<count>/<interval> where <interval> is ISO-8601 duration (must start with 'P')
 * <p>
 * Examples:
 * timeout="PT0.005S"              // 5 ms
 * retry="R3/PT0.2S"               // retry 3 times, 200 ms between attempts
 * <p>
 *
 * @author yusu
 */
public class JobPolicy extends BaseElement implements JobPolicyElement {

    // ISO-8601, e.g. PT2S
    private String timeout;

    // e.g. R3/PT0.2S
    private String retry;

    // never/transient/always or bean name
    private String retryOn;

    // incident/continue or bean name
    private String onFailure;

    public JobPolicy() {
    }

    public static JobPolicy of(String timeout, String retry, String retryOn, String onFailure) {
        JobPolicy jobPolicy = new JobPolicy();
        jobPolicy.timeout = timeout;
        jobPolicy.retry = retry;
        jobPolicy.retryOn = retryOn;
        jobPolicy.onFailure = onFailure;
        return jobPolicy;
    }

    @Override
    public String getTimeout() {
        return timeout;
    }

    public void setTimeout(String timeout) {
        this.timeout = timeout;
    }

    @Override
    public String getRetry() {
        return retry;
    }

    public void setRetry(String retry) {
        this.retry = retry;
    }

    @Override
    public String getRetryOn() {
        return retryOn;
    }

    public void setRetryOn(String retryOn) {
        this.retryOn = retryOn;
    }

    @Override
    public String getOnFailure() {
        return onFailure;
    }

    public void setOnFailure(String onFailure) {
        this.onFailure = onFailure;
    }

}
