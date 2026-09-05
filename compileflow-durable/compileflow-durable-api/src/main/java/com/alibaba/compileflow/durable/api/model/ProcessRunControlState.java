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
package com.alibaba.compileflow.durable.api.model;

/**
 * Orthogonal admission state for Run execution.
 *
 * <p>The control state does not replace {@link ProcessRunStatus}: logical
 * lifecycle and operator admission are independent axes. In particular,
 * {@link #PAUSE_REQUESTED} honestly exposes authority that was issued before
 * a Pause request, while {@link #PAUSED} means no new Turn or
 * Effect execution may be admitted.</p>
 *
 * @author yusu
 */
public enum ProcessRunControlState {
    /**
     * Run execution may be admitted.
     */
    ACTIVE,
    /**
     * Previously issued execution authority is converging to a boundary.
     */
    PAUSE_REQUESTED,
    /**
     * Run execution admission is closed.
     */
    PAUSED
}
