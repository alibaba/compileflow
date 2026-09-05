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
package com.alibaba.compileflow.engine.core.model.action;

import com.alibaba.compileflow.engine.core.model.AbstractElement;

/**
 * Authoring policy for one synchronous action invocation.
 *
 * <p>The policy controls in-process timeout, retry, and terminal failure behavior.
 * It does not model a persistent job or durable incident lifecycle.</p>
 *
 * @author yusu
 */
public final class InvocationPolicy extends AbstractElement {
    /**
     * Complete invocation budget including attempts and backoff. {@code null} = no overall limit.
     */
    private String timeout;
    /**
     * Budget for one attempt. {@code null} = no per-attempt limit; the remaining overall budget still applies.
     */
    private String attemptTimeout;
    /**
     * Maximum attempts including the initial attempt. {@code null} = 1 (no retry).
     */
    private Integer maxAttempts;
    /**
     * ISO-8601 duration, e.g. {@code PT0.2S}. {@code null} = PT1S.
     */
    private String initialBackoff;
    /**
     * Backoff multiplier for exponential retry delay. {@code null} = 1.0 (fixed backoff).
     */
    private Double backoffMultiplier;
    /**
     * Upper bound for retry delay with exponential backoff. {@code null} = 100 × initialBackoff.
     */
    private String maxBackoff;
    /**
     * Retry delay randomization. {@code null} = full jitter.
     */
    private RetryJitter jitter;
    /**
     * Retry trigger: {@code never}/{@code transient}/{@code always} or policy name. {@code null} = always.
     */
    private String retryOn;
    /**
     * Failure resolution: {@code propagate}/{@code continue} or handler name. {@code null} = propagate.
     */
    private String onFailure;

    public InvocationPolicy() {
    }

    public String getTimeout() {
        return timeout;
    }

    public void setTimeout(String timeout) {
        this.timeout = timeout;
    }

    public String getAttemptTimeout() {
        return attemptTimeout;
    }

    public void setAttemptTimeout(String attemptTimeout) {
        this.attemptTimeout = attemptTimeout;
    }

    public Integer getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(Integer maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public String getInitialBackoff() {
        return initialBackoff;
    }

    public void setInitialBackoff(String initialBackoff) {
        this.initialBackoff = initialBackoff;
    }

    public Double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    public void setBackoffMultiplier(Double backoffMultiplier) {
        this.backoffMultiplier = backoffMultiplier;
    }

    public String getMaxBackoff() {
        return maxBackoff;
    }

    public void setMaxBackoff(String maxBackoff) {
        this.maxBackoff = maxBackoff;
    }

    public RetryJitter getJitter() {
        return jitter;
    }

    public void setJitter(RetryJitter jitter) {
        this.jitter = jitter;
    }

    public String getRetryOn() {
        return retryOn;
    }

    public void setRetryOn(String retryOn) {
        this.retryOn = retryOn;
    }

    public String getOnFailure() {
        return onFailure;
    }

    public void setOnFailure(String onFailure) {
        this.onFailure = onFailure;
    }
}
