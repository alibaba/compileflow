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
package com.alibaba.compileflow.engine.core.runtime.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig;
import com.alibaba.compileflow.engine.core.java.compiler.JdkJavaCompiler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeExpressionCompilerTest {
    @TempDir
    Path debugDirectory;

    @Test
    void generatesReadableExpressionSource() throws Exception {
        RuntimeExpression value = expression("amount + 1", RuntimeExpression.Kind.VALUE);
        RuntimeExpression condition = expression("amount > 0", RuntimeExpression.Kind.CONDITION);
        RuntimeExpressionCompiler compiler = new RuntimeExpressionCompiler(new JdkJavaCompiler(),
                JavaDiagnosticsConfig.builder().debugOutputDirectory(debugDirectory).build());

        CompiledExpressionEvaluator evaluator =
                compiler.compile("order.payment", List.of(value, condition), getClass().getClassLoader());

        assertThat(evaluator.evaluate(value, Map.of("amount", 2), Map.of())).isEqualTo(3);
        assertThat(evaluator.evaluateCondition(condition, Map.of("amount", 2), Map.of())).isTrue();

        Path sourcePath;
        try (var sources = Files.walk(debugDirectory.resolve("source"))) {
            sourcePath = sources
                .filter(path -> path.toString().endsWith(".java"))
                .findFirst()
                .orElseThrow();
        }
        String source = Files.readString(sourcePath);
        assertThat(source)
            .contains("package com.alibaba.compileflow.generated.expression;")
            .contains("@Generated(value = \"compileflow.runtime-expressions/v2\")")
            .contains("        Integer amount = (Integer) arguments[0];")
            .contains("        return amount + 1;")
            .contains("case 1 -> ConditionSemantics.isTrue(expression1(arguments));")
            .contains("private static Boolean expression1(Object[] arguments)")
            .contains("        return amount > 0;")
            .doesNotContain("return (", "java.lang.Integer")
            .doesNotEndWith("\n\n}\n");
        assertThat(source.lines().mapToInt(String::length).max().orElseThrow()).isLessThanOrEqualTo(120);
    }

    @Test
    void bindingNamesCannotCollideWithGeneratedParameters() {
        RuntimeExpression expression = new RuntimeExpression("arguments + _arguments", RuntimeExpression.Kind.VALUE,
                List.of(new RuntimeExpression.Binding("arguments", "int"),
                        new RuntimeExpression.Binding("_arguments", "int")));
        RuntimeExpressionCompiler compiler =
                new RuntimeExpressionCompiler(new JdkJavaCompiler(), JavaDiagnosticsConfig.defaults());

        CompiledExpressionEvaluator evaluator =
                compiler.compile("binding.names", List.of(expression), getClass().getClassLoader());

        assertThat(evaluator.evaluate(expression, Map.of("arguments", 20, "_arguments", 22), Map.of())).isEqualTo(42);
    }

    @Test
    void conditionBindingCannotShadowTheGeneratedTruthConversion() {
        RuntimeExpression expression = new RuntimeExpression("ConditionSemantics", RuntimeExpression.Kind.CONDITION,
                List.of(new RuntimeExpression.Binding("ConditionSemantics", "java.lang.Boolean")));
        RuntimeExpressionCompiler compiler =
                new RuntimeExpressionCompiler(new JdkJavaCompiler(), JavaDiagnosticsConfig.defaults());

        CompiledExpressionEvaluator evaluator =
                compiler.compile("condition.names", List.of(expression), getClass().getClassLoader());

        assertThat(evaluator.evaluateCondition(expression, Map.of("ConditionSemantics", true), Map.of())).isTrue();
        assertThat(evaluator.evaluateCondition(expression,
                java.util.Collections.singletonMap("ConditionSemantics", null), Map.of()))
            .isFalse();
    }

    @Test
    void rejectsNonBooleanConditionDuringCompilation() {
        RuntimeExpression expression = new RuntimeExpression("42", RuntimeExpression.Kind.CONDITION, List.of());
        RuntimeExpressionCompiler compiler =
                new RuntimeExpressionCompiler(new JdkJavaCompiler(), JavaDiagnosticsConfig.defaults());

        assertThatThrownBy(() -> compiler.compile("condition.type", List.of(expression), getClass().getClassLoader()))
            .isInstanceOf(com.alibaba.compileflow.engine.CompileFlowException.class);
    }

    @Test
    void preservesExpressionSourceExactly() {
        RuntimeExpression expression = expression("  amount + 1  ", RuntimeExpression.Kind.VALUE);

        assertThat(expression.source()).isEqualTo("  amount + 1  ");
    }

    private static RuntimeExpression expression(String source, RuntimeExpression.Kind kind) {
        return new RuntimeExpression(source, kind,
                List.of(new RuntimeExpression.Binding("amount", Integer.class.getName())));
    }
}
