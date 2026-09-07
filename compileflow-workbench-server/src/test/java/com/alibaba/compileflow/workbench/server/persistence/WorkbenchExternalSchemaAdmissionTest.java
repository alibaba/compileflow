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
    private static final boolean MYSQL_CONFIGURED = !POSTGRES_CONFIGURED
            && "MYSQL".equalsIgnoreCase(System.getenv("COMPILEFLOW_WORKBENCH_SERVER_CONFIG_DATABASE_PROVIDER"))
            && hasText(System.getenv("SPRING_DATASOURCE_URL"))
            && System.getenv("SPRING_DATASOURCE_URL").startsWith("jdbc:mysql:")
            && hasText(System.getenv("SPRING_DATASOURCE_USERNAME"));
    private static final String URL = POSTGRES_CONFIGURED
            ? System.getenv("COMPILEFLOW_WORKBENCH_TEST_POSTGRES_URL")
            : MYSQL_CONFIGURED ? System.getenv("SPRING_DATASOURCE_URL") : H2_URL;
    private static final String USERNAME = POSTGRES_CONFIGURED
            ? System.getenv("COMPILEFLOW_WORKBENCH_TEST_POSTGRES_USERNAME")
            : MYSQL_CONFIGURED ? System.getenv("SPRING_DATASOURCE_USERNAME") : "sa";
    private static final String PASSWORD = POSTGRES_CONFIGURED
            ? System.getenv("COMPILEFLOW_WORKBENCH_TEST_POSTGRES_PASSWORD")
            : MYSQL_CONFIGURED ? System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "") : "";
    @Autowired
    private Flyway flyway;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.datasource.username", () -> USERNAME);
        registry.add("spring.datasource.password", () -> PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> POSTGRES_CONFIGURED
                ? "org.postgresql.Driver"
                : MYSQL_CONFIGURED ? "com.mysql.cj.jdbc.Driver" : "org.h2.Driver");
        registry.add("compileflow.workbench.server.database.provider", () -> MYSQL_CONFIGURED ? "MYSQL" : "POSTGRESQL");
        registry.add("spring.flyway.locations", WorkbenchExternalSchemaAdmissionTest::workbenchMigrationLocation);
        registry.add("spring.flyway.table", () -> "cf_workbench_schema_history");
    }

    private static String deployMigrationLocation() {
        return POSTGRES_CONFIGURED
                ? "classpath:db/compileflow-deploy/postgres/migration"
                : MYSQL_CONFIGURED
                ? "classpath:db/compileflow-deploy/mysql/migration"
                : "classpath:db/compileflow-deploy-h2/migration";
    }

    private static String workbenchMigrationLocation() {
        return "classpath:db/compileflow-workbench-server/" + (MYSQL_CONFIGURED ? "mysql" : "postgres") + "/migration";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Test
    void startsAfterExternalMigrationWithoutApplicationDdl() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
        assertThat(flyway.info().pending()).isEmpty();
    }

    static final class ExternalMigration implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            Flyway deployFlyway = Flyway
                .configure()
                .dataSource(URL, USERNAME, PASSWORD)
                .locations(deployMigrationLocation())
                .table("cf_deploy_schema_history")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .cleanDisabled(true)
                .load();
            deployFlyway.migrate();
            Flyway workbenchFlyway = Flyway
                .configure()
                .dataSource(URL, USERNAME, PASSWORD)
                .locations(workbenchMigrationLocation())
                .table("cf_workbench_schema_history")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .cleanDisabled(true)
                .load();
            workbenchFlyway.migrate();
        }
    }
}
