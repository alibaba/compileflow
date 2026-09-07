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
package com.alibaba.compileflow.deploy.spring.boot.autoconfigure.mysql;

import com.alibaba.compileflow.deploy.mysql.MySqlDeployStore;
import com.alibaba.compileflow.deploy.spi.store.DeployStore;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.CompileFlowDeployStoreAutoConfiguration;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.CompileFlowDeploymentProperties;
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
 * MySQL composition for the complete Deploy persistence authority.
 */
@AutoConfiguration(before = CompileFlowDeployStoreAutoConfiguration.class, afterName = "org.springframework.boot."
        + "jdbc.autoconfigure.DataSourceAutoConfiguration")
@ConditionalOnClass({MySqlDeployStore.class, org.flywaydb.core.Flyway.class})
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "compileflow.deploy", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "compileflow.deploy", name = "control-plane-enabled", havingValue = "true", matchIfMissing = true)
@Conditional(MySqlDeployProviderCondition.class)
public class CompileFlowDeployMySqlAutoConfiguration {
    public static final String DEPLOY_DATA_SOURCE_BEAN_NAME = "compileFlowDeployDataSource";

    @Bean
    @ConditionalOnMissingBean(DeployStore.class)
    public DeployStore deployStore(@Qualifier(DEPLOY_DATA_SOURCE_BEAN_NAME) ObjectProvider<DataSource> deployDataSources,
            ObjectProvider<DataSource> dataSources, CompileFlowDeploymentProperties properties) {
        DataSource dataSource = selectDataSource(deployDataSources, dataSources);
        new DeployMySqlSchemaInitializer(dataSource).initialize(properties.getDatabase().isMigrate());
        return new MySqlDeployStore(dataSource, properties.getRouting().getKeyPrefix());
    }

    static DataSource selectDataSource(ObjectProvider<DataSource> dedicated, ObjectProvider<DataSource> application) {
        DataSource dataSource = dedicated.getIfAvailable();
        if (dataSource == null) {
            dataSource = application.getIfAvailable();
        }
        if (dataSource == null) {
            throw new IllegalStateException("CompileFlow Deploy MySQL Provider requires one DataSource");
        }
        return dataSource;
    }
}
