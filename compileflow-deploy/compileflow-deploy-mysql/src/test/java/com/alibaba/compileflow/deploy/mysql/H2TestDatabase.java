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
package com.alibaba.compileflow.deploy.mysql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;

public final class H2TestDatabase {
    private static final String SCHEMA_RESOURCE = "/com/alibaba/compileflow/deploy/mysql/test/h2-schema.sql";

    private H2TestDatabase() {
    }

    public static DataSource createInMemoryDataSource() {
        return createInMemoryDataSource("test_" + System.nanoTime());
    }

    public static DataSource createInMemoryDataSource(String dbName) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1;MODE=MySQL;LOCK_MODE=1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        initializeSchema(dataSource);
        return dataSource;
    }

    private static void initializeSchema(DataSource dataSource) {
        String schema = readSchema();
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            for (String sql : schema.split(";")) {
                String statement = sql.trim();
                if (!statement.isEmpty()) {
                    stmt.execute(statement);
                }
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Failed to initialize the Deploy H2 test schema", failure);
        }
    }

    private static String readSchema() {
        try (InputStream stream = H2TestDatabase.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Deploy H2 test schema: " + SCHEMA_RESOURCE);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Failed to read the Deploy H2 test schema", failure);
        }
    }
}
