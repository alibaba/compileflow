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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import com.alibaba.compileflow.deploy.control.repository.JdbcProcessAliasRepository;
import com.alibaba.compileflow.deploy.control.repository.JdbcProcessVersionRepository;
import com.alibaba.compileflow.deploy.control.repository.ProcessAliasRepository;
import com.alibaba.compileflow.deploy.control.repository.ProcessVersionRepository;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CompileFlowRepositoryAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CompileFlowRepositoryAutoConfiguration.class))
        .withPropertyValues("compileflow.deploy.enabled=true");

    @Test
    void shouldNotFallbackToInMemoryRepositoriesWithoutDataSource() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(ProcessVersionRepository.class);
            assertThat(context).doesNotHaveBean(ProcessAliasRepository.class);
        });
    }

    @Test
    void shouldCreateJdbcRepositoriesWhenDataSourceExists() {
        contextRunner
            .withBean(DataSource.class, () -> mock(DataSource.class))
            .run(context -> {
                assertThat(context).hasSingleBean(ProcessVersionRepository.class);
                assertThat(context).hasSingleBean(ProcessAliasRepository.class);
                assertThat(context.getBean(ProcessVersionRepository.class)).isInstanceOf(
                        JdbcProcessVersionRepository.class);
                assertThat(context.getBean(ProcessAliasRepository.class)).isInstanceOf(JdbcProcessAliasRepository.class);
            });
    }

    @Test
    void shouldBackOffWhenApplicationProvidesRepositories() {
        ProcessVersionRepository versionRepository = mock(ProcessVersionRepository.class);
        ProcessAliasRepository aliasRepository = mock(ProcessAliasRepository.class);
        contextRunner
            .withBean(DataSource.class, () -> mock(DataSource.class))
            .withBean(ProcessVersionRepository.class, () -> versionRepository)
            .withBean(ProcessAliasRepository.class, () -> aliasRepository)
            .run(context -> {
                assertThat(context.getBean(ProcessVersionRepository.class)).isSameAs(versionRepository);
                assertThat(context.getBean(ProcessAliasRepository.class)).isSameAs(aliasRepository);
            });
    }
}
