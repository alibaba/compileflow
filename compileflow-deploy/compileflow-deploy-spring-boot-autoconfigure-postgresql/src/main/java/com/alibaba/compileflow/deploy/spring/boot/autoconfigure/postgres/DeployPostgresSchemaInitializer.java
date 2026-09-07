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
package com.alibaba.compileflow.deploy.spring.boot.autoconfigure.postgres;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;

/**
 * Fail-closed PostgreSQL Deploy schema initialization.
 */
final class DeployPostgresSchemaInitializer {
    private final Flyway flyway;

    DeployPostgresSchemaInitializer(DataSource dataSource) {
        this(Flyway
            .configure()
            .dataSource(requirePostgres(dataSource))
            .locations("classpath:db/compileflow-deploy/postgres/migration")
            .table("cf_deploy_schema_history")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .failOnMissingLocations(true)
            .load());
    }

    DeployPostgresSchemaInitializer(Flyway flyway) {
        this.flyway = Objects.requireNonNull(flyway, "flyway");
    }

    private static DataSource requirePostgres(DataSource dataSource) {
        DataSource required = Objects.requireNonNull(dataSource, "dataSource");
        try (Connection connection = required.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            if (!"PostgreSQL".equals(product)) {
                throw new IllegalStateException(
                        "CompileFlow Deploy PostgreSQL Provider requires a PostgreSQL DataSource, but found " + product);
            }
            return required;
        } catch (SQLException failure) {
            throw new IllegalStateException("Cannot verify the CompileFlow Deploy PostgreSQL DataSource", failure);
        }
    }

    void initialize(boolean migrate) {
        if (migrate) {
            flyway.migrate();
        }
        flyway.validate();
        MigrationInfo[] pending = flyway.info().pending();
        if (pending.length > 0) {
            throw new IllegalStateException(
                    "CompileFlow Deploy schema has " + pending.length
                    + " pending Flyway migration(s); apply them with the deployment migration identity");
        }
    }
}
