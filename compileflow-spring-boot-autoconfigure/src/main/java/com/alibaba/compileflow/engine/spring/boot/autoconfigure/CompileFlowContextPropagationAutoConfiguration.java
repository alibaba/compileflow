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

import com.alibaba.compileflow.engine.spi.execution.ProcessContextPropagator;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Adapts Micrometer Context Propagation when that library is present.
 *
 * @author yusu
 */
@AutoConfiguration(before = CompileFlowEngineAutoConfiguration.class)
@ConditionalOnClass(name = "io.micrometer.context.ContextSnapshot")
public class CompileFlowContextPropagationAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public ProcessContextPropagator processContextPropagator() {
        ContextSnapshotFactory snapshotFactory = ContextSnapshotFactory.builder().clearMissing(true).build();
        return () -> {
            ContextSnapshot snapshot = snapshotFactory.captureAll();
            return () -> {
                ContextSnapshot.Scope scope = snapshot.setThreadLocals();
                return scope::close;
            };
        };
    }
}
