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
package com.alibaba.compileflow.deploy.spring.boot.autoconfigure;

import com.alibaba.compileflow.deploy.spi.store.DeployStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Enforces one complete persistence authority for the Deploy bounded context.
 */
@AutoConfiguration(afterName = {"com.alibaba.compileflow.deploy.spring.boot.autoconfigure.postgres."
        + "CompileFlowDeployPostgresAutoConfiguration",
        "com.alibaba.compileflow.deploy.spring.boot.autoconfigure.mysql.CompileFlowDeployMySqlAutoConfiguration"}, before = {CompileFlowDeployControlPlaneAutoConfiguration.class,
        CompileFlowDeployOutboxAutoConfiguration.class})
@ConditionalOnClass(DeployStore.class)
@ConditionalOnProperty(prefix = "compileflow.deploy", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "compileflow.deploy", name = "control-plane-enabled", havingValue = "true", matchIfMissing = true)
public class CompileFlowDeployStoreAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(DeployStore.class)
    public DeployStore missingDeployStore() {
        throw new IllegalStateException(
                "CompileFlow Deploy requires exactly one DeployStore; add one PostgreSQL or "
                + "MySQL Deploy starter, select an available Provider when both are present, or provide one complete "
                + "custom DeployStore");
    }
}
