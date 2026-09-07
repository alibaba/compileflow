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
package com.alibaba.compileflow.durable.spring.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class CompileFlowDurableConfigurationMetadataTest {
    private static final String METADATA = "META-INF/spring-configuration-metadata.json";
    private static final Pattern DOCUMENTED_PROPERTY =
            Pattern.compile("(?m)^[|]\\s*`(compileflow[.]durable[.][a-z0-9]+(?:[.-][a-z0-9]+)*)`\\s*[|]");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void publishesUniqueDocumentedGroupedPropertiesWithTruthfulDefaults() throws IOException {
        Map<String, JsonNode> properties = readProperties();
        assertDefault(properties, "compileflow.durable.enabled", false);
        assertDefault(properties, "compileflow.durable.database.migrate", false);
        assertDefault(properties, "compileflow.durable.runtime-mode", "COMPILED");
        assertDefault(properties, "compileflow.durable.call.max-depth", 32);
        assertDefault(properties, "compileflow.durable.shutdown.timeout", "15s");
        assertDefault(properties, "compileflow.durable.worker.enabled", true);
        assertDefault(properties, "compileflow.durable.worker.lease-duration", "30s");
        assertDefault(properties, "compileflow.durable.worker.idle-poll-delay", "100ms");
        assertDefault(properties, "compileflow.durable.worker.turn-fault-backoff", "1s");
        assertDefault(properties, "compileflow.durable.worker.turn-max-steps", 10_000);
        assertDefault(properties, "compileflow.durable.worker.max-active-iterations", 32);
        assertDefault(properties, "compileflow.durable.worker.turn-concurrency", 2);
        assertDefault(properties, "compileflow.durable.worker.effect-concurrency", 8);
        assertDefault(properties, "compileflow.durable.outbox.concurrency", 2);
        assertDefault(properties, "compileflow.durable.outbox.retry.initial-delay", "1s");
        assertDefault(properties, "compileflow.durable.outbox.retry.max-delay", "1m");
        assertDefault(properties, "compileflow.durable.outbox.retry.max-attempts", 100);
        assertDefault(properties, "compileflow.durable.maintenance.interval", "1s");
        assertDefault(properties, "compileflow.durable.maintenance.batch-size", 100);
        assertDefault(properties, "compileflow.durable.retention.interval", "1h");
        assertDefault(properties, "compileflow.durable.cache.runtime-max-size", 256);

        assertThat(properties)
            .doesNotContainKeys("compileflow.durable.worker-enabled", "compileflow.durable.poll-interval",
                    "compileflow.durable.capability-backoff", "compileflow.durable.outbox-retry-delay",
                    "compileflow.durable.program-mode", "compileflow.durable.worker.idle-poll-interval",
                    "compileflow.durable.max-call-depth", "compileflow.durable.shutdown-timeout");
        assertThat(properties
            .entrySet()
            .stream()
            .filter(entry -> !entry.getValue().has("defaultValue"))
            .map(Map.Entry::getKey)
            .toList())
            .containsExactlyInAnyOrder("compileflow.durable.database.provider", "compileflow.durable.worker.id",
                    "compileflow.durable.retention.terminal-run", "compileflow.durable.retention.unused-process",
                    "compileflow.durable.retention.consumed-occurrence",
                    "compileflow.durable.java-diagnostics.debug.output-directory");
        assertThat(documentedProperties(findRepositoryRoot().resolve("docs/en/configuration.md")))
            .containsExactlyInAnyOrderElementsOf(properties.keySet());
        assertThat(documentedProperties(findRepositoryRoot().resolve("docs/zh/configuration.md")))
            .containsExactlyInAnyOrderElementsOf(properties.keySet());
    }

    @Test
    void publishesValueHintsForEveryDurableStoreProvider() throws IOException {
        Map<String, Set<String>> hints = new HashMap<>();
        for (JsonNode hint : readMetadata().path("hints")) {
            Set<String> values = new HashSet<>();
            for (JsonNode value : hint.path("values")) {
                values.add(value.path("value").stringValue());
                assertThat(value.path("description").stringValue()).isNotBlank();
            }
            hints.put(hint.path("name").stringValue(), values);
        }

        assertThat(hints.get("compileflow.durable.database.provider")).containsExactlyInAnyOrder("POSTGRESQL", "MYSQL");
    }

    private static Map<String, JsonNode> readProperties() throws IOException {
        JsonNode root = readMetadata();
        Map<String, JsonNode> result = new HashMap<>();
        for (JsonNode property : root.path("properties")) {
            String name = property.path("name").stringValue();
            assertThat(result.put(name, property)).as("duplicate metadata property %s", name).isNull();
            assertThat(property.path("description").stringValue()).as(name).isNotBlank();
        }
        return result;
    }

    private static JsonNode readMetadata() throws IOException {
        Path metadata = moduleClassesDirectory().resolve(METADATA);
        assertThat(metadata).isRegularFile();
        try (InputStream input = Files.newInputStream(metadata)) {
            return OBJECT_MAPPER.readTree(input);
        }
    }

    private static Path moduleClassesDirectory() throws IOException {
        try {
            Path testClasses = Path.of(CompileFlowDurableConfigurationMetadataTest.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
            return testClasses.getParent().resolve("classes");
        } catch (URISyntaxException e) {
            throw new IOException("Cannot locate this module's generated configuration metadata", e);
        }
    }

    private static void assertDefault(Map<String, JsonNode> properties, String name, Object expected) {
        assertThat(properties).containsKey(name);
        assertThat(properties.get(name).path("defaultValue")).as(name).isEqualTo(OBJECT_MAPPER.valueToTree(expected));
    }

    private static Set<String> documentedProperties(Path reference) throws IOException {
        Matcher matcher = DOCUMENTED_PROPERTY.matcher(Files.readString(reference));
        Set<String> properties = new HashSet<>();
        while (matcher.find()) {
            properties.add(matcher.group(1));
        }
        return properties;
    }

    private static Path findRepositoryRoot() {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            if (Files.isRegularFile(directory.resolve("docs/en/configuration.md"))
                    && Files.isRegularFile(directory.resolve("pom.xml"))) {
                return directory;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Cannot locate the CompileFlow repository root");
    }
}
