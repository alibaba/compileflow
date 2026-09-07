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
package com.alibaba.compileflow.durable.spring.boot.autoconfigure.mysql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;

/**
 * Fail-closed initialization of the MySQL Durable schema and its isolated Flyway history.
 */
public final class DurableMySqlSchemaInitializer {
    private final Flyway flyway;
    private boolean initialized;

    public DurableMySqlSchemaInitializer(DataSource dataSource) {
        this(Flyway
            .configure()
            .dataSource(requireMySql(dataSource))
            .locations("classpath:db/compileflow-durable/mysql/migration")
            .table("cf_durable_schema_history")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .failOnMissingLocations(true)
            .load());
    }

    DurableMySqlSchemaInitializer(Flyway flyway) {
        this.flyway = Objects.requireNonNull(flyway, "flyway");
    }

    private static DataSource requireMySql(DataSource dataSource) {
        DataSource required = Objects.requireNonNull(dataSource, "dataSource");
        try (Connection connection = required.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            if (!"MySQL".equals(product)) {
                throw new IllegalStateException(
                        "CompileFlow Durable MySQL Provider requires a MySQL DataSource, but found " + product);
            }
            return required;
        } catch (SQLException failure) {
            throw new IllegalStateException("Cannot verify the CompileFlow Durable MySQL DataSource", failure);
        }
    }

    public synchronized void initialize(boolean migrate) {
        if (initialized) {
            return;
        }
        if (migrate) {
            flyway.migrate();
        }
        flyway.validate();
        MigrationInfo[] pending = flyway.info().pending();
        if (pending.length > 0) {
            throw new IllegalStateException(
                    "CompileFlow Durable schema has " + pending.length
                    + " pending Flyway migration(s); apply them with the deployment migration identity before starting this runtime");
        }
        initialized = true;
    }
}
