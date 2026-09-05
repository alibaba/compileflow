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
package com.alibaba.compileflow.durable.api;

import static org.assertj.core.api.Assertions.assertThat;
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

class DurableApiShapeTest {
    private static final String API_PACKAGE = "com.alibaba.compileflow.durable.api";
    private static final Map<String, Set<String>> SUPPORTED_TOP_LEVEL_TYPES = Map.of(API_PACKAGE,
            Set.of("DurableOperatorService", "DurableProcessEngine"), API_PACKAGE + ".command",
            Set.of("EffectResolutionDecision", "OutboxResolutionDecision", "PauseRunCommand", "ResolveEffectCommand",
                    "ResolveOutboxEventCommand", "ResumeRunCommand"), API_PACKAGE + ".effect",
            Set.of("EffectReconcileOutcome", "EffectRecoveryPlan"), API_PACKAGE + ".error",
            Set.of("DurableErrorCode", "DurableProcessException"), API_PACKAGE + ".model",
            Set.of("ActiveEffect", "ActiveTimer", "ActiveWait", "ActiveWorkCursor", "ActiveWorkPage", "ActiveWorkQuery",
                    "ActiveWorkSummary", "ActiveWork", "EffectReadinessCode", "EffectExecutionStatus",
                    "OutboxEventCursor", "OutboxEventPage", "OutboxEventQuery", "OutboxEventStatus", "OutboxEvent",
                    "AuditPrincipal", "ProcessRunControlState", "ProcessRunControl", "ProcessRunCursor", "ProcessRunId",
                    "ProcessRunPage", "ProcessRunQuery", "ProcessRunResult", "ProcessRunRetryState", "ProcessRunStatus",
                    "ProcessRun", "ProcessTimelineCursor", "ProcessTimelineEventCode", "ProcessTimelineEvent",
                    "ProcessTimelinePage", "ProcessTimelineQuery", "RunRetryCode", "WaitToken"),
            API_PACKAGE + ".validation",
            Set.of("DurableEnumSets", "DurableIdentifiers", "DurableNumbers", "DurablePayload"));

    @Test
    void keepsSupportedTypesExplicitlyAllowlisted() throws Exception {
        assertThat(publicTopLevelTypes()).as("complete Durable API allowlist").isEqualTo(SUPPORTED_TOP_LEVEL_TYPES);
    }

    private static Map<String, Set<String>> publicTopLevelTypes() throws Exception {
        URL location = DurableProcessEngine.class.getProtectionDomain().getCodeSource().getLocation();
        Path codeSource = Path.of(location.toURI());
        String packagePath = "com/alibaba/compileflow/durable/";
        Set<String> classNames = new HashSet<>();
        if (Files.isDirectory(codeSource)) {
            Path packageDirectory = codeSource.resolve(packagePath);
            try (var files = Files.walk(packageDirectory)) {
                files
                    .filter(Files::isRegularFile)
                    .map(codeSource::relativize)
                    .map(Path::toString)
                    .map(path -> path.replace('\\', '/'))
                    .filter(DurableApiShapeTest::isTopLevelClassFile)
                    .map(DurableApiShapeTest::className)
                    .forEach(classNames::add);
            }
        } else {
            try (JarFile jar = new JarFile(codeSource.toFile())) {
                jar
                    .stream()
                    .map(entry -> entry.getName())
                    .filter(name -> name.startsWith(packagePath))
                    .filter(DurableApiShapeTest::isTopLevelClassFile)
                    .map(DurableApiShapeTest::className)
                    .forEach(classNames::add);
            }
        }
        classNames.removeIf(name -> !Modifier.isPublic(loadType(name).getModifiers()));

        Map<String, Set<String>> typesByPackage = new HashMap<>();
        for (String name : classNames) {
            int separator = name.lastIndexOf('.');
            typesByPackage
                .computeIfAbsent(name.substring(0, separator), ignored -> new HashSet<>())
                .add(name.substring(separator + 1));
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
            return Class.forName(name, false, DurableProcessEngine.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Unable to load Durable API type " + name, exception);
        }
    }
}
