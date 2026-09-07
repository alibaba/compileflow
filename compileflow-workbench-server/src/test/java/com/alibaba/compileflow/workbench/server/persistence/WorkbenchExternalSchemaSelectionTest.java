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
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class WorkbenchExternalSchemaSelectionTest {
    @Test
    void selectedDataSourceAndMigrationProviderRemainConsistent() {
        Map<String, Supplier<Object>> properties = new HashMap<>();
        WorkbenchExternalSchemaAdmissionTest.databaseProperties(properties::put);
        String url = (String) properties.get("spring.datasource.url").get();
        boolean mysql = url.startsWith("jdbc:mysql:");
        String driver = mysql
                ? "com.mysql.cj.jdbc.Driver"
                : url.startsWith("jdbc:postgresql:") ? "org.postgresql.Driver" : "org.h2.Driver";
        assertThat(properties.get("spring.datasource.driver-class-name").get()).isEqualTo(driver);
        assertThat(properties.get("compileflow.workbench.server.database.provider").get())
            .isEqualTo(mysql ? "MYSQL" : "POSTGRESQL");
        assertThat(properties.get("spring.flyway.locations").get())
            .isEqualTo("classpath:db/compileflow-workbench-server/" + (mysql ? "mysql" : "postgres") + "/migration");
    }
}
