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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;

/**
 * Fail-closed MySQL Deploy schema initialization.
 */
final class DeployMySqlSchemaInitializer {
    private final Flyway flyway;

    DeployMySqlSchemaInitializer(DataSource dataSource) {
        this(Flyway
            .configure()
            .dataSource(requireMySql(dataSource))
            .locations("classpath:db/compileflow-deploy/mysql/migration")
            .table("cf_deploy_schema_history")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .failOnMissingLocations(true)
            .load());
    }

    DeployMySqlSchemaInitializer(Flyway flyway) {
        this.flyway = Objects.requireNonNull(flyway, "flyway");
    }

    private static DataSource requireMySql(DataSource dataSource) {
        DataSource required = Objects.requireNonNull(dataSource, "dataSource");
        try (Connection connection = required.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            if (!"MySQL".equals(product)) {
                throw new IllegalStateException(
                        "CompileFlow Deploy MySQL Provider requires a MySQL DataSource, but found " + product);
            }
            return required;
        } catch (SQLException failure) {
            throw new IllegalStateException("Cannot verify the CompileFlow Deploy MySQL DataSource", failure);
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
