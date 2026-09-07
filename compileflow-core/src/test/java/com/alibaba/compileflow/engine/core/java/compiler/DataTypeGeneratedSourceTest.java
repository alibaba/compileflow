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
import com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig;
import com.alibaba.compileflow.engine.core.type.DataTypes;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DataTypeGeneratedSourceTest {
    @Test
    void generatedCharacterDefaultCompilesAsJava17() throws Exception {
        String className = "com.alibaba.compileflow.generated.DataTypeBoundaryProbe";
        String characterLiteral = DataTypes.generateDefaultValueCode(char.class, "'").expression();
        String source = "package com.alibaba.compileflow.generated;\n"
                + "public class DataTypeBoundaryProbe {\n    char delimiter = " + characterLiteral + ";\n}\n";
        JavaCompileOptions options =
                new JavaCompileOptions(JavaDiagnosticsConfig.DebugSymbols.LINES, getClass().getClassLoader());
        Map<String, byte[]> classes = new HashMap<>();

        new JdkJavaCompiler()
            .compile(JavaSource.of(source, className), (name, bytes) -> classes.put(name, bytes.clone()), options);

        assertThat(classes).containsKey(className);
    }
}
