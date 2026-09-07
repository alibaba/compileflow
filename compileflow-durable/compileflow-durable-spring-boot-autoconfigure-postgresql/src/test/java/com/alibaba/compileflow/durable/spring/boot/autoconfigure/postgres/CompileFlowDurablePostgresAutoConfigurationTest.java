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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CompileFlowDurablePostgresAutoConfigurationTest {
    @Test
    void dedicatedDurableDataSourceTakesPrecedenceOverApplicationDataSource() {
        DataSource dedicated = mock(DataSource.class);
        DataSource application = mock(DataSource.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<DataSource> dedicatedProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<DataSource> applicationProvider = mock(ObjectProvider.class);
        when(dedicatedProvider.getIfAvailable()).thenReturn(dedicated);
        when(applicationProvider.getIfAvailable()).thenReturn(application);

        assertThat(CompileFlowDurablePostgresAutoConfiguration.selectDataSource(dedicatedProvider, applicationProvider))
            .isSameAs(dedicated);
    }

    @Test
    void customStoreCompletelyBacksOffPostgresComposition() {
        DurableStore customStore = mock(DurableStore.class);
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CompileFlowDurablePostgresAutoConfiguration.class))
            .withPropertyValues("compileflow.durable.enabled=true")
            .withBean(DataSource.class, () -> mock(DataSource.class))
            .withBean(DurableStore.class, () -> customStore)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).getBean(DurableStore.class).isSameAs(customStore);
            });
    }
}
