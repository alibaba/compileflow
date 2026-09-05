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
package com.alibaba.compileflow.engine.preflight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class ProcessPreflightReportTest {
    private static ProcessPreflightReport reportWith(ProcessPreflightReport.ItemStatus status) {
        return ProcessPreflightReport
            .builder()
            .code("flow")
            .addItem(ProcessPreflightReport.ItemType.LINT, status, 1L, "result")
            .totalDurationMs(1L)
            .build();
    }

    @Test
    void derivesOverallStatusFromStageOutcomes() {
        ProcessPreflightReport passed = reportWith(ProcessPreflightReport.ItemStatus.PASS);
        ProcessPreflightReport failed = reportWith(ProcessPreflightReport.ItemStatus.FAIL);
        ProcessPreflightReport timedOut = reportWith(ProcessPreflightReport.ItemStatus.TIMEOUT);

        assertThat(passed.getOverallStatus()).isEqualTo(ProcessPreflightReport.OverallStatus.PASS);
        assertThat(failed.getOverallStatus()).isEqualTo(ProcessPreflightReport.OverallStatus.FAIL);
        assertThat(timedOut.getOverallStatus()).isEqualTo(ProcessPreflightReport.OverallStatus.FAIL);
    }

    @Test
    void rejectsStructurallyInvalidReports() {
        assertThatThrownBy(() -> ProcessPreflightReport.builder().build())
            .isInstanceOf(NullPointerException.class)
            .hasMessage("code");
        assertThatThrownBy(() -> ProcessPreflightReport.builder().code("flow").build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at least one item");
        assertThatThrownBy(() -> ProcessPreflightReport
            .builder()
            .code("flow")
            .addItem(null, ProcessPreflightReport.ItemStatus.PASS, 1L, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("type");
        assertThatThrownBy(() -> ProcessPreflightReport
            .builder()
            .code("flow")
            .addItem(ProcessPreflightReport.ItemType.LINT, null, 1L, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("status");
        assertThatThrownBy(() -> ProcessPreflightReport
            .builder()
            .code("flow")
            .addItem(ProcessPreflightReport.ItemType.LINT, ProcessPreflightReport.ItemStatus.PASS, -1L, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("durationMs");
    }

    @Test
    void rejectsNonCanonicalCodeAndReturnsAnImmutableItemSnapshot() {
        assertThatThrownBy(() -> ProcessPreflightReport
            .builder()
            .code("  flow  ")
            .addItem(ProcessPreflightReport.ItemType.LINT, ProcessPreflightReport.ItemStatus.PASS, 1L, "ok")
            .totalDurationMs(2L)
            .build())
            .hasMessageContaining("surrounding whitespace");

        ProcessPreflightReport report = ProcessPreflightReport
            .builder()
            .code("flow")
            .addItem(ProcessPreflightReport.ItemType.LINT, ProcessPreflightReport.ItemStatus.PASS, 1L, "ok")
            .totalDurationMs(2L)
            .build();
        assertThatThrownBy(() -> report.getItems().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
