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
package com.alibaba.compileflow.engine.core.runtime.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.spi.script.ScriptProgram;
import com.alibaba.compileflow.engine.spi.script.ScriptException;
import com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QlExpressScriptExecutorTest {
    private static ScriptProgramSpec spec(String source) {
        return new ScriptProgramSpec("qlexpress", source, List.of(), null);
    }

    private static QlExpressScriptExecutor executor() {
        return new QlExpressScriptExecutor(QlExpressScriptExecutorTest.class.getClassLoader());
    }

    private static Object evaluate(QlExpressScriptExecutor executor, String source, Map<String, Object> context) {
        return executor.evaluate(executor.compile(spec(source)), context);
    }

    @Test
    void evaluatesExpressionsAndSafeCollectionFunctionsInIsolation() {
        QlExpressScriptExecutor executor = executor();

        assertThat(evaluate(executor, "base + delta", Map.of("base", 2, "delta", 3))).isEqualTo(5);
        assertThat(evaluate(executor, "size(values)", Map.of("values", List.of(1, 2, 3)))).isEqualTo(3);
    }

    @Test
    void isolatedModeRejectsHostObjectMethodAccess() {
        QlExpressScriptExecutor executor = executor();

        assertThatThrownBy(() -> evaluate(executor, "value.intValue()", Map.of("value", 7)))
            .isInstanceOf(ScriptException.class)
            .hasMessageContaining("QL script evaluation failed");
    }

    @Test
    void validatesAndCompilesWithoutPersistingProviderArtifacts() {
        QlExpressScriptExecutor executor = executor();

        executor.validate(spec("value + 1"));
        ScriptProgram program = executor.compile(spec("value + 1"));

        assertThat(program.language()).isEqualTo("qlexpress");
        assertThat(executor.evaluate(program, Map.of("value", 7))).isEqualTo(8);
    }

    @Test
    void closeRejectsFurtherUse() {
        QlExpressScriptExecutor executor = executor();
        ScriptProgram program = executor.compile(spec("value + 1"));
        executor.evaluate(program, Map.of("value", 1));

        executor.close();

        assertThatThrownBy(() -> executor.evaluate(program, Map.of("value", 1)))
            .isInstanceOf(ScriptException.class)
            .hasMessage("QL script executor is closed");
    }
}
