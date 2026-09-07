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
package com.alibaba.compileflow.deploy.postgres;

import com.alibaba.compileflow.deploy.jdbc.JdbcDeployStore;
import javax.sql.DataSource;

/**
 * PostgreSQL physical implementation of the complete Deploy persistence authority.
 *
 * @author yusu
 */
public final class PostgresDeployStore extends JdbcDeployStore {
    public PostgresDeployStore(DataSource dataSource, String projectionKeyPrefix) {
        super(dataSource, projectionKeyPrefix, Dialect.POSTGRESQL);
    }

    static String deleteDeliveredOlderThanSql() {
        return deleteDeliveredOlderThanSql(Dialect.POSTGRESQL);
    }
}
