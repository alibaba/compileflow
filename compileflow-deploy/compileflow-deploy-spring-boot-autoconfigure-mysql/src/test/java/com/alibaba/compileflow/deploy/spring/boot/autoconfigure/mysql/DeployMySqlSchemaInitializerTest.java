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
package com.alibaba.compileflow.deploy.spring.boot.autoconfigure.mysql;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.junit.jupiter.api.Test;

class DeployMySqlSchemaInitializerTest {
    @Test
    void rejectsNonMySqlDataSourceBeforeSchemaAccess() throws Exception {
        assertThatThrownBy(() -> new DeployMySqlSchemaInitializer(dataSource("PostgreSQL")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("requires a MySQL DataSource")
            .hasMessageContaining("PostgreSQL");
    }

    @Test
    void validatesExternallyMigratedSchema() {
        Flyway flyway = flywayWithPending();

        new DeployMySqlSchemaInitializer(flyway).initialize(false);

        verify(flyway, never()).migrate();
        verify(flyway).validate();
        verify(flyway.info()).pending();
    }

    @Test
    void migratesThenValidatesEvaluationSchema() {
        Flyway flyway = flywayWithPending();

        new DeployMySqlSchemaInitializer(flyway).initialize(true);

        verify(flyway).migrate();
        verify(flyway).validate();
        verify(flyway.info()).pending();
    }

    @Test
    void rejectsPendingMigrationWhenDdlIsExternallyManaged() {
        Flyway flyway = flywayWithPending(mock(MigrationInfo.class));

        assertThatThrownBy(() -> new DeployMySqlSchemaInitializer(flyway).initialize(false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("pending Flyway migration")
            .hasMessageContaining("deployment migration identity");
        verify(flyway, never()).migrate();
        verify(flyway).validate();
    }

    private static DataSource dataSource(String productName) throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn(productName);
        return dataSource;
    }

    private static Flyway flywayWithPending(MigrationInfo... pending) {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService information = mock(MigrationInfoService.class);
        when(flyway.info()).thenReturn(information);
        when(information.pending()).thenReturn(pending);
        return flyway;
    }
}
