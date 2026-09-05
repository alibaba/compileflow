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
package com.alibaba.compileflow.workbench.server.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Proves that externally applied migrations remain subject to the complete
 * Workbench product-root startup admission path.
 *
 * @author yusu
 */
@ActiveProfiles("test")
@ContextConfiguration(initializers = WorkbenchExternalSchemaAdmissionTest.ExternalMigration.class)
@SpringBootTest(properties = {"compileflow.workbench.server.database.migrate=false",
        "spring.datasource.generate-unique-name=false"})
class WorkbenchExternalSchemaAdmissionTest {
    private static final String H2_URL = "jdbc:h2:mem:workbench-external-schema;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
    private static final boolean POSTGRES_CONFIGURED = hasText(System.getenv("COMPILEFLOW_WORKBENCH_TEST_POSTGRES_URL"))
            && hasText(System.getenv("COMPILEFLOW_WORKBENCH_TEST_POSTGRES_USERNAME"))
            && hasText(System.getenv("COMPILEFLOW_WORKBENCH_TEST_POSTGRES_PASSWORD"));
    private static final String URL =
            POSTGRES_CONFIGURED ? System.getenv("COMPILEFLOW_WORKBENCH_TEST_POSTGRES_URL") : H2_URL;
    private static final String USERNAME =
            POSTGRES_CONFIGURED ? System.getenv("COMPILEFLOW_WORKBENCH_TEST_POSTGRES_USERNAME") : "sa";
    private static final String PASSWORD =
            POSTGRES_CONFIGURED ? System.getenv("COMPILEFLOW_WORKBENCH_TEST_POSTGRES_PASSWORD") : "";
    @Autowired
    private Flyway flyway;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.datasource.username", () -> USERNAME);
        registry.add("spring.datasource.password", () -> PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> POSTGRES_CONFIGURED
                ? "org.postgresql.Driver"
                : "org.h2.Driver");
        registry.add("spring.flyway.locations", () -> String.join(",", migrationLocations()));
    }

    private static String[] migrationLocations() {
        return new String[] {POSTGRES_CONFIGURED
                ? "classpath:db/compileflow-deploy/migration"
                : "classpath:db/compileflow-deploy-h2/migration",
                "classpath:db/compileflow-workbench-server/migration"};
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Test
    void startsAfterExternalMigrationWithoutApplicationDdl() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");
        assertThat(flyway.info().pending()).isEmpty();
    }

    static final class ExternalMigration implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            Flyway
                .configure()
                .dataSource(URL, USERNAME, PASSWORD)
                .locations(migrationLocations())
                .cleanDisabled(true)
                .load()
                .migrate();
        }
    }
}
