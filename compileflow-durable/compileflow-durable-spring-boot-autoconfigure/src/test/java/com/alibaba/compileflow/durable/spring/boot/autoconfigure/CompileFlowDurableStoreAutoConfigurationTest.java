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
package com.alibaba.compileflow.durable.spring.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CompileFlowDurableStoreAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CompileFlowDurableStoreAutoConfiguration.class))
        .withPropertyValues("compileflow.durable.enabled=true");

    @Test
    void enabledDurableFailsClosedWithoutAStoreAuthority() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause()
                .hasMessageContaining("requires exactly one DurableStore");
        });
    }

    @Test
    void completeCustomStoreIsTheOnlyAuthority() {
        DurableStore customStore = mock(DurableStore.class);
        runner
            .withBean(DurableStore.class, () -> customStore)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).getBean(DurableStore.class).isSameAs(customStore);
            });
    }
}
