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

import com.alibaba.compileflow.engine.spring.boot.autoconfigure.CompileFlowCoreAutoConfiguration;
import com.alibaba.compileflow.deploy.control.repository.JdbcProcessAliasRepository;
import com.alibaba.compileflow.deploy.control.repository.JdbcProcessVersionRepository;
import com.alibaba.compileflow.deploy.control.repository.ProcessAliasRepository;
import com.alibaba.compileflow.deploy.control.repository.ProcessVersionRepository;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configures JDBC repositories for process versions and aliases.
 *
 * @author yusu
 */
@AutoConfiguration(after = CompileFlowCoreAutoConfiguration.class, afterName = "org.springframework.boot.jdbc."
        + "autoconfigure.DataSourceAutoConfiguration")
@ConditionalOnClass(name = "com.alibaba.compileflow.deploy.control.repository.ProcessVersionRepository")
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "compileflow.deploy", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "compileflow.deploy", name = "control-plane-enabled", havingValue = "true", matchIfMissing = true)
public class CompileFlowRepositoryAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(ProcessVersionRepository.class)
    public ProcessVersionRepository processVersionRepositoryJdbc(DataSource dataSource) {
        return new JdbcProcessVersionRepository(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean(ProcessAliasRepository.class)
    public ProcessAliasRepository processAliasRepositoryJdbc(DataSource dataSource) {
        return new JdbcProcessAliasRepository(dataSource);
    }
}
