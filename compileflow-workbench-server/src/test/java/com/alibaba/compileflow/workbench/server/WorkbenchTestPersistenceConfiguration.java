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
package com.alibaba.compileflow.workbench.server;

import com.alibaba.compileflow.deploy.mysql.MySqlDeployStore;
import com.alibaba.compileflow.deploy.postgres.PostgresDeployStore;
import com.alibaba.compileflow.deploy.spi.store.DeployStore;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Test-only H2 composition; production always selects an explicit PostgreSQL or MySQL Provider.
 */
@Profile("test")
@Configuration(proxyBeanMethods = false)
class WorkbenchTestPersistenceConfiguration {
    @Bean
    DeployStore testDeployStore(DataSource dataSource) {
        String product = databaseProduct(dataSource);
        String migrationLocation = switch (product) {
            case "PostgreSQL" -> "classpath:db/compileflow-deploy/postgres/migration";
            case "MySQL" -> "classpath:db/compileflow-deploy/mysql/migration";
            default -> "classpath:db/compileflow-deploy-h2/migration";
        };
        Flyway
            .configure()
            .dataSource(dataSource)
            .locations(migrationLocation)
            .table("cf_deploy_schema_history")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .cleanDisabled(true)
            .load()
            .migrate();
        return "MySQL".equals(product)
                ? new MySqlDeployStore(dataSource, "compileflow.deploy.")
                : new PostgresDeployStore(dataSource, "compileflow.deploy.");
    }

    private static String databaseProduct(DataSource dataSource) {
        try (var connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName();
        } catch (SQLException failure) {
            throw new IllegalStateException("Cannot inspect the Workbench test DataSource", failure);
        }
    }
}
