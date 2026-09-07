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
package com.alibaba.compileflow.engine.core.runtime.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.alibaba.compileflow.engine.core.runtime.RuntimeTestFixtures.rejectingProcessCallInvoker;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.config.ProcessExecutorConfig;
import com.alibaba.compileflow.engine.core.concurrent.ProcessEngineExecutors;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EngineExecutionContextComponentTest {
    private ProcessEngineExecutors executors;

    @AfterEach
    void tearDown() {
        if (executors != null) {
            executors.close();
        }
    }

    @Test
    void rejectsConcreteDeclaredTypesWhenResolverReturnsNull() {
        EngineExecutionContext context = context(ProcessComponentResolver.disabled());

        assertThatThrownBy(() -> context.component("counterService", CounterService.class))
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("Process component is not available");
    }

    @Test
    void rejectsAbstractDeclaredTypesWhenResolverReturnsNull() {
        EngineExecutionContext context = context(ProcessComponentResolver.disabled());

        assertThatThrownBy(() -> context.component("counterService", Runnable.class))
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("Process component is not available");
    }

    @Test
    void rejectsResolverTypeMismatch() {
        EngineExecutionContext context = context(resolverReturning(new CounterService()));

        assertThatThrownBy(() -> context.component("counterService", HashMap.class))
            .isInstanceOf(CompileFlowException.ConfigurationException.class)
            .hasMessageContaining("but java.util.HashMap was required");
    }

    @Test
    void returnsTheResolverOwnedComponent() {
        CounterService service = new CounterService();
        EngineExecutionContext context = context(resolverReturning(service));

        assertThat(context.component("counterService", CounterService.class)).isSameAs(service);
    }

    private EngineExecutionContext context(ProcessComponentResolver resolver) {
        executors = ProcessEngineExecutors.create("component-test",
                ProcessExecutorConfig.builder().runtimeLoadMaxConcurrency(1).actionTimeoutMaxConcurrency(1).build());
        return EngineExecutionContext
            .builder()
            .namespace("default")
            .processCode("demo")
            .modelType(ProcessModelType.TBBPM)
            .processCallInvoker(rejectingProcessCallInvoker())
            .executors(executors)
            .componentResolver(resolver)
            .scriptExecutors(ScriptExecutorRegistry.from(List.of()))
            .build();
    }

    static final class CounterService {
        private int value;

        int increment() {
            return ++value;
        }
    }

    private static ProcessComponentResolver resolverReturning(Object component) {
        return new ProcessComponentResolver() {
            @Override
            public <T> T resolve(String name, Class<T> requiredType) {
                if (!requiredType.isInstance(component)) {
                    throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_003,
                            "Process component '" + name + "' is of type " + component.getClass().getName() + " but "
                            + requiredType.getName() + " was required");
                }
                return requiredType.cast(component);
            }
        };
    }
}
