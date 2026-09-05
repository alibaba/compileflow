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
package com.alibaba.compileflow.engine.core.java.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InMemoryCompilationTest {
    @Test
    void compiledClassesExposeDefensiveSnapshots() {
        String className = "com.example.Generated";
        byte[] bytes = new byte[] {1, 2, 3};
        MemoryClassOutput output = new MemoryClassOutput(className);

        output.writeClass(className, bytes);
        bytes[0] = 9;
        CompiledClasses compiledClasses = output.finish();
        Map<String, byte[]> firstSnapshot = compiledClasses.copyClassBytes();

        assertThat(firstSnapshot.get(className)).containsExactly(1, 2, 3);
        firstSnapshot.get(className)[0] = 9;
        assertThat(compiledClasses.copyClassBytes().get(className)).containsExactly(1, 2, 3);
        assertThatThrownBy(() -> firstSnapshot.put("com.example.Other", new byte[] {4}))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void classOutputEnforcesNamesBytesDuplicatesAndLifecycle() {
        String className = "com.example.Generated";
        MemoryClassOutput output = new MemoryClassOutput(className);

        assertThatThrownBy(() -> output.writeClass("com/example/Generated", new byte[] {1}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid Java class name");
        assertThatThrownBy(() -> output.writeClass(className, new byte[0]))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not be empty");

        output.writeClass(className, new byte[] {1});
        assertThatThrownBy(() -> output.writeClass(className, new byte[] {2}))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate compiled class");
        output.finish();
        assertThatThrownBy(() -> output.writeClass("com.example.Other", new byte[] {3}))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already finished");
    }

    @Test
    void classOutputRequiresThePrimaryClass() {
        MemoryClassOutput output = new MemoryClassOutput("com.example.Generated");
        output.writeClass("com.example.Generated$Nested", new byte[] {1});

        assertThatThrownBy(output::finish)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("did not emit primary class");
    }

    @Test
    void compilerLoadsPrimaryAndNestedClassesEntirelyFromMemory() throws Exception {
        String className = "com.example.InMemoryProbe";
        String source =
                "package com.example;\n" + "public class InMemoryProbe {\n"
                + "    public int value() { return new Nested().value(); }\n"
                + "    static class Nested { int value() { return 42; } }\n" + "}\n";
        JavaCompileOptions options =
                new JavaCompileOptions(JavaDiagnosticsConfig.DebugSymbols.LINES, getClass().getClassLoader());
        MemoryClassOutput output = new MemoryClassOutput(className);

        new JdkJavaCompiler().compile(JavaSource.of(source, className), output, options);
        CompiledClasses compiledClasses = output.finish();
        CompiledClassLoader classLoader = new CompiledClassLoader(getClass().getClassLoader(), compiledClasses);
        Class<?> compiledClass = classLoader.loadClass(className);
        Object instance = compiledClass.getDeclaredConstructor().newInstance();

        assertThat(compiledClasses.copyClassBytes()).containsKeys(className, className + "$Nested");
        assertThat(compiledClass.getClassLoader()).isSameAs(classLoader);
        assertThat(compiledClass.getMethod("value").invoke(instance)).isEqualTo(42);
    }

    @Test
    void javaSourceRejectsPathLikeAndMalformedClassNames() {
        assertThatThrownBy(() -> JavaSource.of("class Generated {}", "com.example.Generated."))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JavaSource.of("class Generated {}", "../Generated"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JavaSource.of("class Generated {}", "com.example.class"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
