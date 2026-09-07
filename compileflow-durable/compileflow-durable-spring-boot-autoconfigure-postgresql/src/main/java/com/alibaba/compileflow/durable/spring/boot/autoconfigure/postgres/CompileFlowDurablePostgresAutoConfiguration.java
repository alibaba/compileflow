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

import com.alibaba.compileflow.durable.postgres.PostgresDurableStore;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import com.alibaba.compileflow.durable.spring.boot.autoconfigure.CompileFlowDurableStoreAutoConfiguration;
import com.alibaba.compileflow.durable.spring.boot.autoconfigure.properties.CompileFlowDurableProperties;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

/**
 * PostgreSQL Durable Store and schema composition.
 *
 * @author yusu
 */
@AutoConfiguration(before = CompileFlowDurableStoreAutoConfiguration.class, afterName = {"org.springframework.boot."
        + "jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"})
@ConditionalOnClass({PostgresDurableStore.class, org.flywaydb.core.Flyway.class})
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "compileflow.durable", name = "enabled", havingValue = "true")
@Conditional(PostgresDurableProviderCondition.class)
public class CompileFlowDurablePostgresAutoConfiguration {
    /**
     * Name of the optional DataSource dedicated to Durable authority state.
     */
    public static final String DURABLE_DATA_SOURCE_BEAN_NAME = "compileFlowDurableDataSource";

    @Bean
    @ConditionalOnMissingBean(DurableStore.class)
    public DurableStore durableStore(
            @Qualifier(DURABLE_DATA_SOURCE_BEAN_NAME) ObjectProvider<DataSource> durableDataSources,
            ObjectProvider<DataSource> dataSources, CompileFlowDurableProperties properties) {
        DataSource dataSource = selectDataSource(durableDataSources, dataSources);
        new DurablePostgresSchemaInitializer(dataSource).initialize(properties.getDatabase().isMigrate());
        return new PostgresDurableStore(dataSource);
    }

    static DataSource selectDataSource(ObjectProvider<DataSource> durableDataSources,
            ObjectProvider<DataSource> dataSources) {
        DataSource dataSource = durableDataSources.getIfAvailable();
        if (dataSource == null) {
            dataSource = dataSources.getIfAvailable();
        }
        if (dataSource == null) {
            throw new IllegalStateException("CompileFlow Durable PostgreSQL Provider requires one DataSource");
        }
        return dataSource;
    }
}
