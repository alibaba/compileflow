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
            .contains("        return ConditionSemantics.isTrue(amount > 0);")
            .doesNotContain("return (", "java.lang.Integer")
            .doesNotEndWith("\n\n}\n");
        assertThat(source.lines().mapToInt(String::length).max().orElseThrow()).isLessThanOrEqualTo(120);
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
