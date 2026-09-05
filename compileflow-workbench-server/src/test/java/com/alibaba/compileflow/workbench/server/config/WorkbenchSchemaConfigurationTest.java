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
package com.alibaba.compileflow.workbench.server.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;

class WorkbenchSchemaConfigurationTest {
    private static MigrationInfoService migrationInformation() {
        MigrationInfoService information = mock(MigrationInfoService.class);
        when(information.pending()).thenReturn(new MigrationInfo[0]);
        return information;
    }

    @Test
    void validatesExternallyMigratedSchemaWithoutApplyingDdl() {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService information = migrationInformation();
        when(flyway.info()).thenReturn(information);

        WorkbenchSchemaConfiguration.schemaMigrationStrategy(false).migrate(flyway);

        verify(flyway, never()).migrate();
        InOrder order = inOrder(flyway, information);
        order.verify(flyway).validate();
        order.verify(flyway).info();
        order.verify(information).pending();
    }

    @Test
    void migratesThenValidatesApplicationManagedSchema() {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService information = migrationInformation();
        when(flyway.info()).thenReturn(information);

        WorkbenchSchemaConfiguration.schemaMigrationStrategy(true).migrate(flyway);

        InOrder order = inOrder(flyway, information);
        order.verify(flyway).migrate();
        order.verify(flyway).validate();
        order.verify(flyway).info();
        order.verify(information).pending();
    }

    @Test
    void rejectsPendingMigrationWhenDdlIsExternallyManaged() {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService information = mock(MigrationInfoService.class);
        when(flyway.info()).thenReturn(information);
        when(information.pending()).thenReturn(new MigrationInfo[] {mock(MigrationInfo.class)});
        FlywayMigrationStrategy strategy = WorkbenchSchemaConfiguration.schemaMigrationStrategy(false);

        assertThatThrownBy(() -> strategy.migrate(flyway))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("pending Flyway migration")
            .hasMessageContaining("deployment migration identity");
        verify(flyway, never()).migrate();
        verify(flyway).validate();
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsStartupWhenFlywayAdmissionIsDisabled() {
        ObjectProvider<Flyway> provider = mock(ObjectProvider.class);
        SmartInitializingSingleton guard = new WorkbenchSchemaConfiguration().workbenchSchemaAdmissionGuard(provider);

        assertThatThrownBy(guard::afterSingletonsInstantiated)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("requires Flyway schema admission")
            .hasMessageContaining("spring.flyway.enabled");
    }
}
