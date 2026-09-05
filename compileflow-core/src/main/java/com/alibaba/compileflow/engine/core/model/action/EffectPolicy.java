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
 * Optional bounded Kernel parameters subordinate to
 * {@code Action.execution="effect"}.
 *
 * <p>The policy has no target identity. The primary invocation remains the
 * owning Action. A reconcile Action, when present, is a recovery-only query
 * adapter and must not itself declare Durable execution or another policy.
 *
 * @author yusu
 */
public final class EffectPolicy extends AbstractElement {
    private String recoveryPlanVariable;
    private EffectRecovery recovery;
    private Integer maxAttempts;
    private Integer maxReconcileAttempts;
    private String recoveryDelay;
    private String maxRecoveryDuration;
    private ReconcileAction reconcileAction;

    /**
     * Returns the visible Process variable containing the closed recovery plan
     * for each occurrence, or {@code null} when this policy is static.
     */
    public String getRecoveryPlanVariable() {
        return recoveryPlanVariable;
    }

    public void setRecoveryPlanVariable(String recoveryPlanVariable) {
        this.recoveryPlanVariable = recoveryPlanVariable;
    }

    public EffectRecovery getRecovery() {
        return recovery;
    }

    public void setRecovery(EffectRecovery recovery) {
        this.recovery = recovery;
    }

    public Integer getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(Integer maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Integer getMaxReconcileAttempts() {
        return maxReconcileAttempts;
    }

    public void setMaxReconcileAttempts(Integer maxReconcileAttempts) {
        this.maxReconcileAttempts = maxReconcileAttempts;
    }

    public String getRecoveryDelay() {
        return recoveryDelay;
    }

    public void setRecoveryDelay(String recoveryDelay) {
        this.recoveryDelay = recoveryDelay;
    }

    public String getMaxRecoveryDuration() {
        return maxRecoveryDuration;
    }

    public void setMaxRecoveryDuration(String maxRecoveryDuration) {
        this.maxRecoveryDuration = maxRecoveryDuration;
    }

    public ReconcileAction getReconcileAction() {
        return reconcileAction;
    }

    public void setReconcileAction(ReconcileAction reconcileAction) {
        this.reconcileAction = reconcileAction;
    }
}
