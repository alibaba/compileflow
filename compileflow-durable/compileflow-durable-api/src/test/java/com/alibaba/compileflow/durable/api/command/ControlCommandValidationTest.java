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
package com.alibaba.compileflow.durable.api.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.durable.api.model.AuditPrincipal;
import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import org.junit.jupiter.api.Test;

class ControlCommandValidationTest {
    private static final ProcessRunId RUN_ID = new ProcessRunId("00000000-0000-0000-0000-000000000001");
    private static final AuditPrincipal ACTOR = new AuditPrincipal("operator");

    @Test
    void pauseAndResumeAcceptTheInitialZeroRevision() {
        PauseRunCommand pause = new PauseRunCommand(RUN_ID, 0, ACTOR, "maintenance", null);
        ResumeRunCommand resume = new ResumeRunCommand(RUN_ID, 0, ACTOR, "maintenance", null);

        assertThat(pause.expectedControlRevision()).isZero();
        assertThat(resume.expectedControlRevision()).isZero();
    }

    @Test
    void pauseAndResumeRejectNegativeRevisions() {
        assertThatThrownBy(() -> new PauseRunCommand(RUN_ID, -1, ACTOR, "maintenance", null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResumeRunCommand(RUN_ID, -1, ACTOR, "maintenance", null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
