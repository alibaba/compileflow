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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class CompileFlowDeployMySqlAutoConfigurationTest {
    @Test
    void dedicatedDeployDataSourceTakesPrecedenceOverApplicationDataSource() {
        DataSource dedicated = mock(DataSource.class);
        DataSource application = mock(DataSource.class);

        assertThat(CompileFlowDeployMySqlAutoConfiguration.selectDataSource(provider(dedicated), provider(application)))
            .isSameAs(dedicated);
    }

    @Test
    void applicationDataSourceIsUsedWhenNoDedicatedDataSourceExists() {
        DataSource application = mock(DataSource.class);

        assertThat(CompileFlowDeployMySqlAutoConfiguration.selectDataSource(provider(null), provider(application)))
            .isSameAs(application);
    }

    @Test
    void missingDataSourceFailsClosed() {
        assertThatThrownBy(() -> CompileFlowDeployMySqlAutoConfiguration.selectDataSource(provider(null), provider(null)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("CompileFlow Deploy MySQL Provider requires one DataSource");
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<DataSource> provider(DataSource value) {
        ObjectProvider<DataSource> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
