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
package com.alibaba.compileflow.deploy.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MySqlDeployMigrationContractTest {
    private static final String MIGRATION = "/db/compileflow-deploy/mysql/migration/V1__deploy_baseline.sql";

    @Test
    void baselineUsesMySqlNativeIdentityAndStorageSemantics() throws IOException {
        String sql;
        try (InputStream input = MySqlDeployMigrationContractTest.class.getResourceAsStream(MIGRATION)) {
            assertThat(input).as("MySQL deploy migration").isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql)
            .doesNotContain("cf_process_identity")
            .contains("CREATE TABLE cf_process_version")
            .doesNotContain("fk_process_version_identity")
            .contains("ENGINE = InnoDB")
            .contains("COLLATE utf8mb4_0900_bin")
            .contains("SIGNAL SQLSTATE '45000'")
            .contains("sequence > 1 AND from_phase IS NOT NULL AND from_phase = 'IN_PROGRESS'")
            .contains("candidate_version IS NOT NULL AND candidate_weight_bps IS NOT NULL")
            .contains("phase = 'IN_PROGRESS' AND active_marker IS NOT NULL AND active_marker = 1")
            .doesNotContain("RETURNING", "::", "JSONB", "TIMESTAMPTZ", "CREATE OR REPLACE FUNCTION");
    }
}
