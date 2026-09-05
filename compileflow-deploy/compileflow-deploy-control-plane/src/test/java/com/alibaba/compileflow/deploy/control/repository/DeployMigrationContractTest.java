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
package com.alibaba.compileflow.deploy.control.repository;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DeployMigrationContractTest {
    private static final String MIGRATION = "/db/compileflow-deploy/migration/V1__deploy_baseline.sql";

    @Test
    void authoritySchemaPhysicallyProtectsImmutableFacts() throws IOException {
        String sql;
        try (InputStream input = DeployMigrationContractTest.class.getResourceAsStream(MIGRATION)) {
            assertThat(input).as("deploy migration").isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql)
            .contains("CREATE TRIGGER trg_process_version_immutable")
            .contains("BEFORE UPDATE OR DELETE\n    ON cf_process_version")
            .contains("CREATE TRIGGER trg_rollout_event_append_only")
            .contains("BEFORE UPDATE OR DELETE\n    ON cf_rollout_event")
            .contains("CONSTRAINT ck_rollout_event_transition")
            .contains("sequence = 1 AND from_phase IS NULL")
            .contains("sequence > 1 AND from_phase = 'IN_PROGRESS'")
            .contains("ERRCODE = '55000'");
    }

    @Test
    void baselineStoresModelTypeOnEachImmutableVersion() throws IOException {
        String sql;
        try (InputStream input = DeployMigrationContractTest.class.getResourceAsStream(MIGRATION)) {
            assertThat(input).as("deploy migration").isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql)
            .contains("CREATE TABLE cf_process_version")
            .contains("model_type    VARCHAR(16)  NOT NULL")
            .contains("PRIMARY KEY (namespace, code, version)")
            .doesNotContain("cf_process_identity")
            .doesNotContain("fk_process_version_identity");
    }
}
