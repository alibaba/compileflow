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
package com.alibaba.compileflow.durable.spring.boot.autoconfigure.postgres;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.junit.jupiter.api.Test;

class DurablePostgresSchemaInitializerTest {
    private static MigrationInfoService migrationInformation() {
        MigrationInfoService information = mock(MigrationInfoService.class);
        when(information.pending()).thenReturn(new MigrationInfo[0]);
        return information;
    }

    @Test
    void rejectsNonPostgresDataSourceBeforeSchemaAccess() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("MySQL");

        assertThatThrownBy(() -> new DurablePostgresSchemaInitializer(dataSource))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("requires a PostgreSQL DataSource")
            .hasMessageContaining("MySQL");
    }

    @Test
    void validatesExternallyMigratedSchemaExactlyOnce() {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService information = migrationInformation();
        when(flyway.info()).thenReturn(information);

        DurablePostgresSchemaInitializer initializer = new DurablePostgresSchemaInitializer(flyway);
        initializer.initialize(false);
        initializer.initialize(false);

        verify(flyway, never()).migrate();
        verify(flyway, times(1)).validate();
        verify(information, times(1)).pending();
    }

    @Test
    void migratesThenValidatesEvaluationSchema() {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService information = migrationInformation();
        when(flyway.info()).thenReturn(information);

        new DurablePostgresSchemaInitializer(flyway).initialize(true);

        verify(flyway).migrate();
        verify(flyway).validate();
        verify(information).pending();
    }

    @Test
    void rejectsPendingMigrationWhenDdlIsExternallyManaged() {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService information = mock(MigrationInfoService.class);
        when(flyway.info()).thenReturn(information);
        when(information.pending()).thenReturn(new MigrationInfo[] {mock(MigrationInfo.class)});

        DurablePostgresSchemaInitializer initializer = new DurablePostgresSchemaInitializer(flyway);

        assertThatThrownBy(() -> initializer.initialize(false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("pending Flyway migration")
            .hasMessageContaining("deployment migration identity");
        verify(flyway, never()).migrate();
        verify(flyway).validate();
    }
}
