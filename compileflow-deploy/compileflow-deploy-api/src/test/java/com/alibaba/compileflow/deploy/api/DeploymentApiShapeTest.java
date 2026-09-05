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
package com.alibaba.compileflow.deploy.api;

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.deploy.api.protocol.json.DeploymentProtocolJson;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

class DeploymentApiShapeTest {
    private static final String API_PACKAGE = "com.alibaba.compileflow.deploy.api";
    private static final Map<String, Set<String>> SUPPORTED_TOP_LEVEL_TYPES = Map.ofEntries(Map.entry(API_PACKAGE,
                    Set.of("ProcessDeploymentService")),
            Map.entry(API_PACKAGE + ".artifact",
                    Set.of("ProcessArtifact", "ProcessArtifactDigest", "ProcessCallBinding")),
            Map.entry(API_PACKAGE + ".command",
                    Set.of("AbortRolloutCommand", "CreateRolloutCommand", "PromoteRolloutCommand",
                            "PublishProcessVersionCommand", "RollbackRolloutCommand", "UpdateCanaryWeightCommand")),
            Map.entry(API_PACKAGE + ".error", Set.of("DeploymentErrorCode", "DeploymentException")),
            Map.entry(API_PACKAGE + ".observability",
                    Set.of("ProcessDeploymentMetrics", "ProcessDeploymentOperationMetrics")),
            Map.entry(API_PACKAGE + ".protocol", Set.of("ProtocolKeyCodec")),
            Map.entry(API_PACKAGE + ".protocol.json", Set.of("DeploymentProtocolJson")),
            Map.entry(API_PACKAGE + ".protocol.artifact",
                    Set.of("ProcessArtifactKeys", "ProcessArtifactParser", "ProcessArtifactPayloads")),
            Map.entry(API_PACKAGE + ".protocol.routing",
                    Set.of("RoutingStateKeys", "RoutingStateParser", "RoutingStatePayloads", "RoutingStateUpdate")),
            Map.entry(API_PACKAGE + ".release", Set.of("DeploymentAudit", "ReleaseMetadata", "ReleaseMetadataKeys")),
            Map.entry(API_PACKAGE + ".rollout",
                    Set.of("ProcessRollout", "RolloutConstraints", "RolloutCursor", "RolloutEvent",
                            "RolloutOperationKind", "RolloutPage", "RolloutPhase", "RolloutQuery", "RolloutStrategy")),
            Map.entry(API_PACKAGE + ".routing", Set.of("ProcessAliasState")),
            Map.entry(API_PACKAGE + ".spi", Set.of("ProcessArtifactSource")),
            Map.entry(API_PACKAGE + ".sync", Set.of("DeploymentSyncChannel")),
            Map.entry(API_PACKAGE + ".version",
                    Set.of("PublishedProcessVersion", "PublishedVersionCursor", "PublishedVersionPage",
                            "PublishedVersionQuery")));

    @Test
    void keepsSupportedTopLevelTypesExplicitlyAllowlisted() throws Exception {
        assertThat(publicTopLevelTypes())
            .as("complete supported deployment API package and type allowlist")
            .isEqualTo(SUPPORTED_TOP_LEVEL_TYPES);
    }

    @Test
    void keepsProtocolJsonConstructionClosed() {
        assertThat(DeploymentProtocolJson.class.getDeclaredConstructors())
            .allSatisfy(constructor -> assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue());
    }

    private static Map<String, Set<String>> publicTopLevelTypes() throws Exception {
        URL location = ProcessDeploymentService.class.getProtectionDomain().getCodeSource().getLocation();
        Path codeSource = Path.of(location.toURI());
        String packagePath = "com/alibaba/compileflow/deploy/";
        Set<String> classNames = new HashSet<>();
        if (Files.isDirectory(codeSource)) {
            Path packageDirectory = codeSource.resolve(packagePath);
            try (var files = Files.walk(packageDirectory)) {
                files
                    .filter(Files::isRegularFile)
                    .map(codeSource::relativize)
                    .map(Path::toString)
                    .map(path -> path.replace('\\', '/'))
                    .filter(DeploymentApiShapeTest::isTopLevelClassFile)
                    .map(DeploymentApiShapeTest::className)
                    .forEach(classNames::add);
            }
        } else {
            try (JarFile jar = new JarFile(codeSource.toFile())) {
                jar
                    .stream()
                    .map(entry -> entry.getName())
                    .filter(name -> name.startsWith(packagePath))
                    .filter(DeploymentApiShapeTest::isTopLevelClassFile)
                    .map(DeploymentApiShapeTest::className)
                    .forEach(classNames::add);
            }
        }
        classNames.removeIf(name -> !Modifier.isPublic(loadType(name).getModifiers()));

        Map<String, Set<String>> typesByPackage = new HashMap<>();
        for (String name : classNames) {
            int separator = name.lastIndexOf('.');
            String packageName = name.substring(0, separator);
            String simpleName = name.substring(separator + 1);
            typesByPackage
                .computeIfAbsent(packageName, ignored -> new HashSet<>())
                .add(simpleName);
        }
        typesByPackage.replaceAll((ignored, types) -> Set.copyOf(types));
        return typesByPackage;
    }

    private static boolean isTopLevelClassFile(String name) {
        return name.endsWith(".class") && !name.contains("$") && !name.endsWith("module-info.class")
                && !name.endsWith("package-info.class");
    }

    private static String className(String classFileName) {
        return classFileName.substring(0, classFileName.length() - ".class".length()).replace('/', '.');
    }

    private static Class<?> loadType(String name) {
        try {
            return Class.forName(name, false, ProcessDeploymentService.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Unable to load deployment type " + name, exception);
        }
    }
}
