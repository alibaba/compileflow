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
package com.alibaba.compileflow.durable.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class DurableKernelSchemaContractTest {
    private static final Pattern TABLE = Pattern.compile("(?m)^CREATE TABLE public\\.(cf_durable_[a-z_]+) ");

    @Test
    void greenfieldV1LayoutContainsOnlyItsDeclaredKernelTables() throws IOException {
        String sql;
        try (var stream = getClass().getResourceAsStream("/db/compileflow-durable/migration/V1__durable_kernel.sql")) {
            assertThat(stream).isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher matcher = TABLE.matcher(sql);
        java.util.Set<String> tables = new java.util.LinkedHashSet<>();
        while (matcher.find()) {
            tables.add(matcher.group(1));
        }

        assertThat(tables)
            .containsExactlyInAnyOrder("cf_durable_process", "cf_durable_run", "cf_durable_run_recovery_process",
                    "cf_durable_wait", "cf_durable_effect", "cf_durable_journal", "cf_durable_outbox");
        assertThat(sql)
            .doesNotContain("cf_durable_worker", "cf_durable_timer", "process_alias", "program_digest", "codec_version",
                    "application_build", "RECONCILING", "REQUIRES_REVIEW", "CANCEL_REQUESTED", "RUN_STARTED");
        assertThat(sql)
            .contains("model_type", "frontier_id", "ck_cf_durable_wait_frontier", "ck_cf_durable_effect_frontier",
                    "CONSTRAINT ck_cf_durable_process_model_type CHECK (model_type IN ('TBBPM', 'BPMN'))",
                    "CONSTRAINT uq_cf_durable_process_definition_digest UNIQUE (definition_digest)",
                    "recovery_mode = 'MANUAL' AND max_attempts = 1", "AND recovery_deadline_ms IS NULL",
                    "recovery_mode = 'RETRY' AND max_attempts >= 2",
                    "substring(continuation_envelope FROM 1 FOR 3) = decode('434644', 'hex')",
                    "substring(input_envelope FROM 1 FOR 3) = decode('434644', 'hex')",
                    "substring(payload_envelope FROM 1 FOR 3) = decode('434644', 'hex')",
                    "CREATE TRIGGER trg_cf_durable_run_root_process",
                    "CREATE TABLE public.cf_durable_run_recovery_process", "fk_cf_durable_run_root_process",
                    "ck_cf_durable_run_process_identity", "ck_cf_durable_effect_readiness",
                    "'PROCESS_RUNTIME_NOT_READY', 'ACTION_NOT_READY',\n" + "            'RECONCILE_ACTION_NOT_READY'",
                    "turn_fault_streak = 0 AND retry_code IS NULL AND retry_observed_at IS NULL",
                    "turn_fault_streak > 0 AND retry_code IS NOT NULL AND retry_observed_at IS NOT NULL",
                    "completed_at IS NULL OR completed_at >= updated_at",
                    "status <> 'RUNNING' OR control_state <> 'PAUSED'",
                    "status NOT IN ('SUCCEEDED', 'FAILED', 'CANCELLED') OR control_state = 'ACTIVE'");
        assertThat(sql).doesNotContain("ck_cf_durable_effect_capability", "EFFECT_INPUT_INVALID");
        String processTable = sql.substring(sql.indexOf("CREATE TABLE public.cf_durable_process"),
                sql.indexOf("CREATE FUNCTION public.cf_durable_process_reject_update"));
        assertThat(processTable).doesNotContain("namespace", "process_version");
    }
}
