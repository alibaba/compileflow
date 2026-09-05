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
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GeneratedCodeCompilerTest {
    @Test
    void shouldTargetJava17() {
        assertThat(JavaCompileOptions.JAVA_RELEASE).isEqualTo("17");
    }

    @Test
    void shouldCompileJava17SourceWithApplicationDependencies() throws Exception {
        String className = "com.alibaba.compileflow.generated.JdkJava17Probe";
        String source =
                "package com.alibaba.compileflow.generated;\n" + "public record JdkJava17Probe(String value) {\n"
                + "    public String trimmed() {\n"
                + "        return org.apache.commons.lang3.StringUtils.trim(value);\n" + "    }\n" + "}\n";
        JavaCompileOptions options =
                new JavaCompileOptions(JavaDiagnosticsConfig.DebugSymbols.LINES, getClass().getClassLoader());
        Map<String, byte[]> classes = new HashMap<>();

        new JdkJavaCompiler()
            .compile(JavaSource.of(source, className), (name, bytes) -> classes.put(name, bytes.clone()), options);

        assertThat(classes).containsKey(className);
        byte[] bytecode = classes.get(className);
        int majorVersion = (Byte.toUnsignedInt(bytecode[6]) << 8) | Byte.toUnsignedInt(bytecode[7]);
        assertThat(majorVersion).isEqualTo(61);
    }

    @Test
    void compilesCompanionUnitsIntoThePrimaryClassLoader() throws Exception {
        String primaryName = "com.alibaba.compileflow.generated.MultiUnitProcess";
        String companionName = "com.alibaba.compileflow.generated.source.Rule";
        String primary =
                """
                package com.alibaba.compileflow.generated;
                public final class MultiUnitProcess {
                    public static int execute(int value) {
                        return com.alibaba.compileflow.generated.source.Rule.doubleValue(value);
                    }
                }
                """;
        JavaSource companion = JavaSource.of("""
                package com.alibaba.compileflow.generated.source;
                public final class Rule {
                    public static int doubleValue(int value) {
                        return value * 2;
                    }
                }
                """,
                companionName);

        Class<?> compiled = new GeneratedClassCompiler()
            .compile(primaryName, primary, List.of(companion), getClass().getClassLoader(), Map.of());

        assertThat(compiled.getMethod("execute", int.class).invoke(null, 21)).isEqualTo(42);
        assertThat(compiled.getClassLoader().loadClass(companionName).getClassLoader()).isSameAs(compiled.getClassLoader());
    }

    @Test
    void reportsTheFailingCompanionSourceAndLocation() {
        String primaryName = "com.alibaba.compileflow.generated.MultiUnitFailure";
        String companionName = "com.alibaba.compileflow.generated.source.BrokenRule";
        String primary =
                """
                package com.alibaba.compileflow.generated;
                public final class MultiUnitFailure {}
                """;
        JavaSource companion = JavaSource.of("""
                package com.alibaba.compileflow.generated.source;
                public final class BrokenRule {
                    public static int execute() {
                        return missing;
                    }
                }
                """,
                companionName, Map.of("source-origin", "BrokenRule.java", "diagnostic-line-offset", "1"));

        assertThatThrownBy(() -> new GeneratedClassCompiler()
            .compile(primaryName, primary, List.of(companion), getClass().getClassLoader(), Map.of()))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("BrokenRule.java:3:")
            .hasMessageContaining("missing");
    }

    @Test
    void shouldRejectPlatformApisNewerThanJava17() {
        String className = "com.alibaba.compileflow.generated.NewerPlatformApiProbe";
        String source =
                "package com.alibaba.compileflow.generated;\n" + "public class NewerPlatformApiProbe {\n"
                + "    public Object virtualThreadBuilder() {\n" + "        return Thread.ofVirtual();\n" + "    }\n"
                + "}\n";

        assertThatThrownBy(() -> new JdkJavaCompiler()
            .compile(JavaSource.of(source, className), (name, bytes) -> {}, JavaCompileOptions.defaults()))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("ofVirtual");
    }

    @Test
    void generatedClassCompilerShouldResolveDependenciesFromItsParentClassLoader() throws Exception {
        String className = "com.alibaba.compileflow.generated.ParentLoaderProbe";
        String source =
                "package com.alibaba.compileflow.generated;\n" + "public class ParentLoaderProbe {\n"
                + "    public String trim(String value) {\n"
                + "        return org.apache.commons.lang3.StringUtils.trim(value);\n" + "    }\n" + "}\n";

        Class<?> compiled = new GeneratedClassCompiler().compile(className, source, getClass().getClassLoader());
        Object instance = compiled.getDeclaredConstructor().newInstance();

        assertThat(compiled.getMethod("trim", String.class).invoke(instance, " value ")).isEqualTo("value");
    }

    @Test
    void compilationMustNotCloseTheCallerOwnedParentClassLoader() throws Exception {
        String className = "com.alibaba.compileflow.generated.ClassLoaderOwnershipProbe";
        String source =
                "package com.alibaba.compileflow.generated;\n" + "public class ClassLoaderOwnershipProbe {\n"
                + "    public int value() { return 42; }\n" + "}\n";
        try (TrackingUrlClassLoader parent = new TrackingUrlClassLoader(getClass().getClassLoader())) {
            JavaCompileOptions options = new JavaCompileOptions(JavaDiagnosticsConfig.DebugSymbols.LINES, parent);

            new JdkJavaCompiler().compile(JavaSource.of(source, className), (name, bytes) -> {}, options);

            assertThat(parent.isClosed()).isFalse();
            assertThat(parent.loadClass("org.apache.commons.lang3.StringUtils"))
                .isSameAs(org.apache.commons.lang3.StringUtils.class);
        }
    }

    private static final class TrackingUrlClassLoader extends URLClassLoader {
        private boolean closed;

        private TrackingUrlClassLoader(ClassLoader parent) {
            super(new URL[0], parent);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }

        private boolean isClosed() {
            return closed;
        }
    }
}
