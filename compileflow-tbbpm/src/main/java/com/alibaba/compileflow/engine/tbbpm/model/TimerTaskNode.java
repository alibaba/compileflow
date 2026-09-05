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
package com.alibaba.compileflow.engine.tbbpm.model;

/**
 * Suspends process progress until a duration or wake-at expression becomes due.
 *
 * <p>Exactly one of {@code duration}, {@code durationExpression}, and
 * {@code wakeAtExpression} must be configured. Whether a runtime can schedule and recover the
 * suspension is decided by target eligibility, not by the TBBPM source model.</p>
 *
 * @author yusu
 */
public class TimerTaskNode extends FlowNode {
    private String duration;
    private String durationExpression;
    private String wakeAtExpression;

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public String getDurationExpression() {
        return durationExpression;
    }

    public void setDurationExpression(String durationExpression) {
        this.durationExpression = durationExpression;
    }

    public String getWakeAtExpression() {
        return wakeAtExpression;
    }

    public void setWakeAtExpression(String wakeAtExpression) {
        this.wakeAtExpression = wakeAtExpression;
    }
}
