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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.alibaba.compileflow.engine.core.runtime.RuntimeTestFixtures.rejectingProcessCallInvoker;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.config.ProcessExecutorConfig;
import com.alibaba.compileflow.engine.core.concurrent.ProcessEngineExecutors;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EngineExecutionContextProcessCallTest {
    private ProcessEngineExecutors executors;
    private ScriptExecutorRegistry scripts;

    @AfterEach
    void tearDown() {
        if (scripts != null) {
            scripts.close();
        }
        if (executors != null) {
            executors.close();
        }
    }

    @Test
    void processCallRequiresABoundCallSite() {
        EngineExecutionContext context = context(null, "root-invocation");

        assertThatThrownBy(() -> context.callProcess("call-payment", Map.of("amount", 10)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not bound");
    }

    private EngineExecutionContext context(EngineExecutionContext parent, String invocationId) {
        if (executors == null) {
            executors = ProcessEngineExecutors.create("process-call-context-test",
                    ProcessExecutorConfig.builder().runtimeLoadMaxConcurrency(1).actionTimeoutMaxConcurrency(1).build());
            scripts = ScriptExecutorRegistry.builtIns(ProcessEngineConfig.defaults().getClassLoader());
        }
        return EngineExecutionContext
            .builder()
            .traceId("trace-1")
            .invocationId(invocationId)
            .namespace("tenant-a")
            .processCode(parent == null ? "parent" : "child")
            .parentContext(parent)
            .modelType(ProcessModelType.TBBPM)
            .processCallInvoker(rejectingProcessCallInvoker())
            .executors(executors)
            .componentResolver(ProcessComponentResolver.disabled())
            .scriptExecutors(scripts)
            .build();
    }
}
