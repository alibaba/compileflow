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

class JavaSourceScriptExecutorTest {
    @Test
    void classifiesInterruptedJavaCodeAsCancellationAndRestoresTheInterrupt() {
        JavaSourceScriptExecutor executor = new JavaSourceScriptExecutor();
        ScriptProgram program = executor.compile(
                new ScriptProgramSpec("java", "Thread.currentThread().interrupt(); Thread.sleep(1); return 1;",
                        List.of(), "int"));

        try {
            assertThatThrownBy(() -> executor.evaluate(program, Map.of()))
                .isInstanceOfSatisfying(ScriptException.class, failure -> {
                    assertThat(failure.kind()).isEqualTo(ScriptException.Kind.CANCELLED);
                    assertThat(failure.getCause()).isInstanceOf(InterruptedException.class);
                });
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void keepsUnloadedNestedClassesAvailableAfterDefiningTheScriptClass() {
        JavaSourceScriptExecutor executor = new JavaSourceScriptExecutor();
        ScriptProgram program = executor.compile(
                new ScriptProgramSpec("java",
                        "return new java.util.function.IntSupplier() { public int getAsInt() { return 42; } }.getAsInt();",
                        List.of(), "int"));
        assertThat(executor.evaluate(program, Map.of())).isEqualTo(42);
        assertThat(executor.evaluate(program, Map.of())).isEqualTo(42);
    }

    @Test
    void compilesMethodBodyWithTypedExplicitInputs() {
        JavaSourceScriptExecutor executor = new JavaSourceScriptExecutor();
        ScriptProgramSpec spec = new ScriptProgramSpec("java",
                "int subtotal = price * quantity;\nreturn subtotal >= 1000 ? subtotal - 100 : subtotal;",
                List.of(new ScriptProgramSpec.Input("price", "int"), new ScriptProgramSpec.Input("quantity", "int")),
                "int");

        executor.validate(spec);
        ScriptProgram program = executor.compile(spec);

        assertThat(executor.evaluate(program, Map.of("price", 200, "quantity", 6))).isEqualTo(1_100);
    }

    @Test
    void declaredInputsCannotCollideWithTheGeneratedContextParameter() {
        JavaSourceScriptExecutor executor = new JavaSourceScriptExecutor();
        ScriptProgramSpec spec = new ScriptProgramSpec("java", "return input + _input;",
                List.of(new ScriptProgramSpec.Input("input", "int"), new ScriptProgramSpec.Input("_input", "int")),
                "int");

        executor.validate(spec);
        ScriptProgram program = executor.compile(spec);

        assertThat(executor.evaluate(program, Map.of("input", 20, "_input", 22))).isEqualTo(42);
    }

    @Test
    void invalidCodeFailsDuringCompilationRatherThanEvaluation() {
        JavaSourceScriptExecutor executor = new JavaSourceScriptExecutor();
        ScriptProgramSpec spec = new ScriptProgramSpec("java", "return missingSymbol;",
                List.of(new ScriptProgramSpec.Input("price", "int")), "int");

        assertThatThrownBy(() -> executor.validate(spec))
            .isInstanceOf(ScriptException.class)
            .extracting(failure -> ((ScriptException) failure).kind())
            .isEqualTo(ScriptException.Kind.INVALID_SOURCE);
    }

    @Test
    void rejectsAResultThatDoesNotMatchTheDeclaredOutputTypeDuringCompilation() {
        JavaSourceScriptExecutor executor = new JavaSourceScriptExecutor();
        ScriptProgramSpec spec = new ScriptProgramSpec("java", "return \"not a number\";", List.of(), "int");

        assertThatThrownBy(() -> executor.validate(spec))
            .isInstanceOf(ScriptException.class)
            .extracting(failure -> ((ScriptException) failure).kind())
            .isEqualTo(ScriptException.Kind.INVALID_SOURCE);
    }

    @Test
    void rejectsApplicationInputTypesInsteadOfCapturingTheHostClasspath() {
        JavaSourceScriptExecutor executor = new JavaSourceScriptExecutor();
        ScriptProgramSpec spec = new ScriptProgramSpec("java", "return input;",
                List.of(new ScriptProgramSpec.Input("input", "com.example.ApplicationDto")), "java.lang.Object");

        assertThatThrownBy(() -> executor.validate(spec))
            .isInstanceOf(ScriptException.class)
            .hasMessageContaining("Java 17 platform types");
    }

    @Test
    void rejectsApplicationOutputTypesBeforeAnyCodeIsRun() {
        JavaSourceScriptExecutor executor = new JavaSourceScriptExecutor();
        ScriptProgramSpec spec =
                new ScriptProgramSpec("java", "return \"ok\";", List.of(), "com.example.ApplicationResult");

        assertThatThrownBy(() -> executor.validate(spec))
            .isInstanceOf(ScriptException.class)
            .hasMessageContaining("Java 17 platform types");
    }

    @Test
    void doesNotCompileAgainstTheEmbeddingApplicationClasspath() {
        JavaSourceScriptExecutor executor = new JavaSourceScriptExecutor();
        ScriptProgramSpec spec = new ScriptProgramSpec("java",
                "return com.alibaba.compileflow.engine.spi.script.ScriptExecutor.class.getName();", List.of(),
                "java.lang.String");

        assertThatThrownBy(() -> executor.validate(spec))
            .isInstanceOf(ScriptException.class)
            .extracting(failure -> ((ScriptException) failure).kind())
            .isEqualTo(ScriptException.Kind.INVALID_SOURCE);
    }

    @Test
    void reportsDiagnosticsAgainstTheUserMethodBody() {
        JavaSourceScriptExecutor executor = new JavaSourceScriptExecutor();
        ScriptProgramSpec spec = new ScriptProgramSpec("java",
                "int subtotal = price * quantity;\nreturn " + "missingSymbol;",
                List.of(new ScriptProgramSpec.Input("price", "int"), new ScriptProgramSpec.Input("quantity", "int")),
                "int");

        assertThatThrownBy(() -> executor.validate(spec)).isInstanceOf(ScriptException.class).hasMessageContaining(
                "line 2:");
    }
}
