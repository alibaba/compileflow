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
package com.alibaba.compileflow.engine.spring.boot.autoconfigure;

import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.spring.boot.autoconfigure.observability.CompileFlowMetricsBinder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers Micrometer binders for CompileFlow engine metrics.
 *
 * @author yusu
 */
@AutoConfiguration(after = CompileFlowCoreAutoConfiguration.class)
@ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
@ConditionalOnBean({ProcessEngine.class, ProcessEngineConfig.class})
public class CompileFlowMetricsAutoConfiguration {
    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean(name = "compileFlowMetricsBinder")
    public MeterBinder compileFlowMetricsBinder(ProcessEngineConfig config,
            ObjectProvider<ProcessEngineRegistry> registryProvider) {
        ProcessEngineRegistry registry = registryProvider.getIfAvailable();
        Collection<ProcessEngineConfig> configurations =
                registry == null ? List.of(config) : registry.getConfigurations().values();
        return new CompileFlowMetricsBinder(configurations);
    }
}
