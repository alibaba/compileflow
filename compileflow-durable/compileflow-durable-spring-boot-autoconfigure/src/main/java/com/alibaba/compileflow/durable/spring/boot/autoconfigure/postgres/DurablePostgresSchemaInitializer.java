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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;

/**
 * Fail-closed initialization of the Durable kernel schema using an isolated
 * Flyway history.
 *
 * <p>Production runtimes normally let a deployment identity apply migrations
 * and call this component with migration disabled. That path still validates
 * checksums and rejects every pending packaged migration before any Durable
 * runtime starts. Local evaluation may explicitly request migration; the
 * resulting schema is validated in the same way.</p>
 *
 * @author yusu
 */
public final class DurablePostgresSchemaInitializer {
    private final Flyway flyway;
    private boolean initialized;

    public DurablePostgresSchemaInitializer(DataSource dataSource) {
        this(Flyway
            .configure()
            .dataSource(requirePostgres(dataSource))
            .locations("classpath:db/compileflow-durable/migration")
            .defaultSchema("public")
            .schemas("public")
            .table("cf_durable_schema_history")
            .failOnMissingLocations(true)
            .load());
    }

    DurablePostgresSchemaInitializer(Flyway flyway) {
        this.flyway = Objects.requireNonNull(flyway, "flyway");
    }

    private static DataSource requirePostgres(DataSource dataSource) {
        DataSource required = Objects.requireNonNull(dataSource, "dataSource");
        try (Connection connection = required.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            if (!"PostgreSQL".equals(product)) {
                throw new IllegalStateException(
                        "CompileFlow Durable PostgreSQL Provider requires a PostgreSQL DataSource, but found " + product);
            }
            return required;
        } catch (SQLException failure) {
            throw new IllegalStateException("Cannot verify the CompileFlow Durable PostgreSQL DataSource", failure);
        }
    }

    /**
     * Migrates when explicitly allowed, then proves the packaged schema is
     * exactly applied.
     *
     * @param migrate whether this runtime may apply DDL
     */
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
