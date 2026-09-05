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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Owns Workbench Server schema migration and fail-closed startup admission.
 *
 * <p>The product root composes the Deploy and Workbench migration locations.
 * Local evaluation may let the Server apply both migrations. Production may
 * instead use a separate DDL identity, but the runtime still validates Flyway
 * checksums and rejects pending migrations before becoming ready.</p>
 *
 * @author yusu
 */
@Configuration(proxyBeanMethods = false)
public class WorkbenchSchemaConfiguration {
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
                        + " pending Flyway migration(s); apply them with the deployment migration identity before starting the Server");
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
}
