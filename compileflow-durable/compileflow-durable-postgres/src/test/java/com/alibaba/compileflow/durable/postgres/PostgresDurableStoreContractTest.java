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
import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import com.alibaba.compileflow.durable.testkit.DurableStoreContract;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Runs the complete Store protocol against an isolated PostgreSQL container.
 *
 * @author yusu
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresDurableStoreContractTest extends DurableStoreContract {
    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName
        .parse("postgres:17.11-alpine3.24" + "@sha256:18cfe3ef5e6815560c98237d6216d1e5119702fb0f3894c8785dd58b8bbe5d73")
        .asCompatibleSubstituteFor("postgres"));
    private DataSource dataSource;

    @Override
    protected DurableStore createEmptyStore() {
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        migrate(dataSource);
        return new PostgresDurableStore(dataSource);
    }

    @Override
    protected void assertWaitCommittedAuthoritySecretDisposed(ProcessRunId runId) throws Exception {
        assertWaitCommittedAuthoritySecretDisposed(dataSource, runId);
    }

    static void assertWaitCommittedAuthoritySecretDisposed(DataSource dataSource, ProcessRunId runId) throws Exception {
        try (var connection = dataSource.getConnection();
                var select = connection.prepareStatement(
                        """
                    SELECT payload_envelope
                      FROM public.cf_durable_outbox
                     WHERE run_id = ? AND event_type = 'WAIT_COMMITTED'
                    """)) {
            select.setObject(1, java.util.UUID.fromString(runId.value()));
            try (var rows = select.executeQuery()) {
                assertThat(rows.next()).isTrue();
                DurableStore.Envelope envelope = DurableStore.Envelope.fromStoredBytes(rows.getBytes(1));
                assertThat(new String(envelope.payload(), StandardCharsets.UTF_8)).isEqualTo("{}");
                assertThat(rows.next()).isFalse();
            }
        }
    }

    static void migrate(DataSource dataSource) {
        Flyway flyway = Flyway
            .configure()
            .dataSource(dataSource)
            .locations("classpath:db/compileflow-durable/migration")
            .cleanDisabled(false)
            .load();
        flyway.clean();
        flyway.migrate();
    }
}
