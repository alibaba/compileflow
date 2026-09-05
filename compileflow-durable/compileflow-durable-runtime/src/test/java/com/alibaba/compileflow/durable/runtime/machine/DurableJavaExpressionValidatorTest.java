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
package com.alibaba.compileflow.durable.runtime.machine;

import static org.assertj.core.api.Assertions.assertThat;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DurableJavaExpressionValidatorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void attributesApplicationTypesFromTheExactDeploymentClassLoader() throws Exception {
        String typeName = "isolated.OrderValue";
        Path source = temporaryDirectory.resolve("isolated/OrderValue.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source,
                "package isolated; public record OrderValue(int amount) {"
                + " public long now() { return System.currentTimeMillis(); }"
                + " public java.util.UUID random() { return java.util.UUID.randomUUID(); } }", StandardCharsets.UTF_8);
        int result = ToolProvider
            .getSystemJavaCompiler()
            .run(null, null, null, "--release", "17", "-d", temporaryDirectory.toString(), source.toString());
        assertThat(result).isZero();

        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        try (URLClassLoader applicationLoader =
                new URLClassLoader(new java.net.URL[] {temporaryDirectory.toUri().toURL()}, getClass().getClassLoader())) {
            thread.setContextClassLoader(applicationLoader);

            assertThat(new DurableJavaExpressionValidator()
                .validate("order.amount() > 0", Map.of("order", typeName), DurableExpressionValidator.TargetType.BOOLEAN))
                .isEmpty();
            assertThat(new DurableJavaExpressionValidator()
                .validate("order.now() > 0", Map.of("order", typeName), DurableExpressionValidator.TargetType.BOOLEAN))
                .isNotEmpty();
            assertThat(new DurableJavaExpressionValidator()
                .validate("order.random() != null", Map.of("order", typeName),
                        DurableExpressionValidator.TargetType.BOOLEAN))
                .isNotEmpty();
        } finally {
            thread.setContextClassLoader(original);
        }
    }
}
