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
package com.alibaba.compileflow.durable.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import com.alibaba.compileflow.durable.testkit.DurableStoreContract;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Runs the complete Durable Store contract against an isolated MySQL 8.4 instance.
 */
@Testcontainers(disabledWithoutDocker = true)
class MySqlDurableStoreContractTest extends DurableStoreContract {
    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName
        .parse("mysql:8.4.7@sha256:0426ec38c7a10aa45ba383887df7878f74ee70e2fd589c7b69207f3577901903")
        .asCompatibleSubstituteFor("mysql"));
    private DataSource dataSource;

    @Override
    protected DurableStore createEmptyStore() {
        dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        // The ordinary test user cannot create triggers with binary logging enabled.
        migrate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword()));
        return new MySqlDurableStore(dataSource);
    }

    @Override
    protected void assertWaitCommittedAuthoritySecretDisposed(ProcessRunId runId) throws Exception {
        try (var connection = dataSource.getConnection();
                var select = connection.prepareStatement(
                        """
                    SELECT payload_envelope
                      FROM cf_durable_outbox
                     WHERE run_id = ? AND event_type = 'WAIT_COMMITTED'
                    """)) {
            select.setString(1, runId.value());
            try (var rows = select.executeQuery()) {
                assertThat(rows.next()).isTrue();
                DurableStore.Envelope envelope = DurableStore.Envelope.fromStoredBytes(rows.getBytes(1));
                assertThat(new String(envelope.payload(), StandardCharsets.UTF_8)).isEqualTo("{}");
                assertThat(rows.next()).isFalse();
            }
        }
    }

    private static void migrate(DataSource dataSource) {
        Flyway flyway = Flyway
            .configure()
            .dataSource(dataSource)
            .locations("classpath:db/compileflow-durable/mysql/migration")
            .cleanDisabled(false)
            .load();
        flyway.clean();
        flyway.migrate();
    }
}
