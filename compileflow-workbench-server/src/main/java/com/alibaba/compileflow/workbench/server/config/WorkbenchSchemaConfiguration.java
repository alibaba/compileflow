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

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Owns Workbench Server schema migration and fail-closed startup admission.
 *
 * <p>This migration history owns only Workbench tables. The selected Deploy Provider owns and
 * validates its independent schema history.</p>
 *
 * @author yusu
 */
@Configuration(proxyBeanMethods = false)
public class WorkbenchSchemaConfiguration {
    private static final String WORKBENCH_HISTORY_TABLE = "cf_workbench_schema_history";

    static String migrationLocation(CompileFlowWorkbenchServerProperties.Database.Provider provider) {
        String directory =
                switch (provider) {
            case POSTGRESQL -> "postgres";
            case MYSQL -> "mysql";
        };
        return "classpath:db/compileflow-workbench-server/" + directory + "/migration";
    }

    static FlywayMigrationStrategy schemaMigrationStrategy(boolean migrate) {
        return flyway -> {
            if (migrate) {
                flyway.migrate();
            }
            flyway.validate();
            MigrationInfo[] pending = flyway.info().pending();
            if (pending.length > 0) {
                throw new IllegalStateException(
                        "CompileFlow Workbench Server schema has " + pending.length
                        + " pending Flyway migration(s); apply them with the Workbench migration identity before starting the Server");
            }
        };
    }

    /**
     * Replaces Spring Boot's migrate-only callback with the Workbench schema
     * admission contract.
     *
     * @param properties immutable Workbench Server configuration
     * @return migration and validation strategy
     */
    @Bean
    public FlywayConfigurationCustomizer workbenchFlywayConfiguration(CompileFlowWorkbenchServerProperties properties) {
        return configuration -> configuration
            .locations(migrationLocation(properties.getDatabase().getProvider()))
            .table(WORKBENCH_HISTORY_TABLE)
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .cleanDisabled(true)
            .failOnMissingLocations(true);
    }

    @Bean
    public FlywayMigrationStrategy workbenchSchemaMigrationStrategy(CompileFlowWorkbenchServerProperties properties) {
        return schemaMigrationStrategy(properties.getDatabase().isMigrate());
    }

    /**
     * Prevents disabling Flyway and thereby bypassing checksum and pending
     * migration validation.
     *
     * @param flywayProvider configured product-root Flyway instance
     * @return singleton startup guard
     */
    @Bean
    public SmartInitializingSingleton workbenchSchemaAdmissionGuard(ObjectProvider<Flyway> flywayProvider) {
        return () -> {
            if (flywayProvider.getIfAvailable() == null) {
                throw new IllegalStateException(
                        "CompileFlow Workbench Server requires Flyway schema admission; do not disable spring.flyway.enabled");
            }
        };
    }

    @Bean
    @Profile("!test")
    public SmartInitializingSingleton workbenchDatabaseProviderGuard(DataSource dataSource,
            CompileFlowWorkbenchServerProperties properties) {
        return () -> requireSelectedProvider(dataSource, properties.getDatabase().getProvider());
    }

    static void requireSelectedProvider(DataSource dataSource,
            CompileFlowWorkbenchServerProperties.Database.Provider provider) {
        try (Connection connection = dataSource.getConnection()) {
            String actual = connection.getMetaData().getDatabaseProductName();
            String expected =
                    provider == CompileFlowWorkbenchServerProperties.Database.Provider.POSTGRESQL
                    ? "PostgreSQL"
                    : "MySQL";
            if (!expected.equals(actual)) {
                throw new IllegalStateException(
                        "CompileFlow Workbench database Provider is " + provider + ", but the DataSource reports " + actual);
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Cannot verify the CompileFlow Workbench DataSource", failure);
        }
    }
}
