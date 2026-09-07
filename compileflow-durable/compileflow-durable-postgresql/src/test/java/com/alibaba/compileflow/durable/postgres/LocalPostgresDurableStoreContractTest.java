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
package com.alibaba.compileflow.durable.postgres;

import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import com.alibaba.compileflow.durable.testkit.DurableStoreContract;
import javax.sql.DataSource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Optional developer/CI contract path for an explicitly supplied PostgreSQL database.
 *
 * @author yusu
 */
@EnabledIfEnvironmentVariable(named = "COMPILEFLOW_DURABLE_POSTGRES_URL", matches = ".+")
class LocalPostgresDurableStoreContractTest extends DurableStoreContract {
    private DataSource dataSource;

    @Override
    protected DurableStore createEmptyStore() {
        dataSource = new DriverManagerDataSource(System.getenv("COMPILEFLOW_DURABLE_POSTGRES_URL"),
                environmentOrDefault("COMPILEFLOW_DURABLE_POSTGRES_USER", "postgres"),
                environmentOrDefault("COMPILEFLOW_DURABLE_POSTGRES_PASSWORD", "postgres"));
        PostgresDurableStoreContractTest.migrate(dataSource);
        return new PostgresDurableStore(dataSource);
    }

    @Override
    protected void assertWaitCommittedAuthoritySecretDisposed(ProcessRunId runId) throws Exception {
        PostgresDurableStoreContractTest.assertWaitCommittedAuthoritySecretDisposed(dataSource, runId);
    }

    private static String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
