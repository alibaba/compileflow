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
package com.alibaba.compileflow.deploy.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class DeploymentProtocolShapeTest {
    private static final Set<String> PUBLIC_TYPES = Set.of("ProcessArtifactCodec", "ProcessArtifactKeys",
            "RoutingStateCodec", "RoutingStateKeys", "RoutingStateUpdate");

    @Test
    void exposesOnlyTheCrossProcessProtocolSurface() throws Exception {
        URL location = ProcessArtifactCodec.class.getProtectionDomain().getCodeSource().getLocation();
        Path packageDirectory = Path.of(location.toURI()).resolve("com/alibaba/compileflow/deploy/protocol");
        try (Stream<Path> files = Files.list(packageDirectory)) {
            Set<String> publicTypes = files
                .filter(path -> path.getFileName().toString().endsWith(".class"))
                .filter(path -> !path.getFileName().toString().contains("$"))
                .map(path -> path.getFileName().toString().replace(".class", ""))
                .filter(DeploymentProtocolShapeTest::isPublic)
                .collect(Collectors.toUnmodifiableSet());
            assertThat(publicTypes).isEqualTo(PUBLIC_TYPES);
        }
    }

    private static boolean isPublic(String simpleName) {
        try {
            return Modifier.isPublic(Class
                .forName("com.alibaba.compileflow.deploy.protocol." + simpleName, false,
                        ProcessArtifactCodec.class.getClassLoader())
                .getModifiers());
        } catch (ClassNotFoundException exception) {
            throw new AssertionError(exception);
        }
    }
}
