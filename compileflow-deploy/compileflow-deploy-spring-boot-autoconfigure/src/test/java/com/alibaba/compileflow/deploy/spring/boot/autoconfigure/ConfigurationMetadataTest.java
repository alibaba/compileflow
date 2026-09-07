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
package com.alibaba.compileflow.deploy.spring.boot.autoconfigure;

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

class ConfigurationMetadataTest {
    private static final String METADATA = "META-INF/spring-configuration-metadata.json";
    private static final Pattern DOCUMENTED_PROPERTY = Pattern.compile(
            "(?m)^\\|(?:[^|\\r\\n]*\\|)*\\s*`(compileflow\\.deploy\\.[a-z0-9]+(?:[.-][a-z0-9]+)*)`\\s*\\|");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static JsonNode readMetadata() throws IOException {
        Path metadata = moduleClassesDirectory().resolve(METADATA);
        assertThat(metadata).isRegularFile();
        try (InputStream input = Files.newInputStream(metadata)) {
            return OBJECT_MAPPER.readTree(input);
        }
    }

    private static Path moduleClassesDirectory() throws IOException {
        try {
            Path testClasses =
                    Path.of(ConfigurationMetadataTest.class
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

    @Test
    void publishesUniqueDeploymentPropertiesWithTruthfulDefaults() throws IOException {
        JsonNode properties = readMetadata().path("properties");
        assertThat(properties.isArray()).isTrue();

        Set<String> names = new HashSet<>();
        Map<String, JsonNode> byName = new HashMap<>();
        for (JsonNode property : properties) {
            String name = property.path("name").stringValue();
            assertThat(name).startsWith("compileflow.deploy.");
            assertThat(property.path("description").stringValue()).as(name).isNotBlank();
            assertThat(names.add(name)).as("duplicate metadata property %s", name).isTrue();
            byName.put(name, property);
        }

        assertDefault(byName, "compileflow.deploy.enabled", false);
        assertDefault(byName, "compileflow.deploy.database.migrate", false);
        assertDefault(byName, "compileflow.deploy.topology", "EMBEDDED");
        assertDefault(byName, "compileflow.deploy.control-plane-enabled", true);
        assertDefault(byName, "compileflow.deploy.runtime-worker-enabled", false);
        assertDefault(byName, "compileflow.deploy.runtime.convergence-timeout", "30s");
        assertDefault(byName, "compileflow.deploy.runtime.installation-concurrency", 1);
        assertDefault(byName, "compileflow.deploy.artifact.mode", "SOURCE");
        assertDefault(byName, "compileflow.deploy.outbox.lease-duration", "1m");
        assertDefault(byName, "compileflow.deploy.outbox.dispatch-batch-size", 50);
        assertDefault(byName, "compileflow.deploy.outbox.retry.initial-delay", "5s");
        assertDefault(byName, "compileflow.deploy.outbox.retry.max-delay", "5m");
        assertDefault(byName, "compileflow.deploy.outbox.retry.max-attempts", 10);
        assertDefault(byName, "compileflow.deploy.reconciliation.mode", "REPAIR");
        assertThat(byName.get("compileflow.deploy.database.provider").has("defaultValue")).isFalse();
        assertThat(byName)
            .doesNotContainKeys("compileflow.deploy.runtime.max-in-flight", "compileflow.deploy.routing.keys",
                    "compileflow.deploy.reconciliation.enabled", "compileflow.deploy.reconciliation.repair-enabled",
                    "compileflow.deploy.outbox.claim-lease", "compileflow.deploy.outbox.max-attempts",
                    "compileflow.deploy.outbox.initial-retry-delay", "compileflow.deploy.outbox.max-retry-delay",
                    "compileflow.deploy.outbox.batch-size", "compileflow.deploy.control.compilation.max-in-flight",
                    "compileflow.deploy.control.compilation.admission-timeout",
                    "compileflow.deploy.control.compilation.lease-timeout",
                    "compileflow.deploy.artifact.require-content-digest");
        assertThat(byName
            .entrySet()
            .stream()
            .filter(entry -> !entry.getValue().has("defaultValue"))
            .map(Map.Entry::getKey)
            .toList())
            .containsExactly("compileflow.deploy.database.provider");
    }

    @Test
    void publishesValueHintsForEveryDeploymentMode() throws IOException {
        Map<String, Set<String>> hints = new HashMap<>();
        for (JsonNode hint : readMetadata().path("hints")) {
            Set<String> values = new HashSet<>();
            for (JsonNode value : hint.path("values")) {
                values.add(value.path("value").stringValue());
                assertThat(value.path("description").stringValue()).isNotBlank();
            }
            hints.put(hint.path("name").stringValue(), values);
        }

        assertThat(hints.get("compileflow.deploy.artifact.mode")).containsExactlyInAnyOrder("SOURCE", "PROJECTION_STORE");
        assertThat(hints.get("compileflow.deploy.topology")).containsExactlyInAnyOrder("EMBEDDED", "DISTRIBUTED");
        assertThat(hints.get("compileflow.deploy.reconciliation.mode"))
            .containsExactlyInAnyOrder("DISABLED", "DETECT", "REPAIR");
        assertThat(hints.get("compileflow.deploy.database.provider")).containsExactlyInAnyOrder("POSTGRESQL", "MYSQL");
    }

    @Test
    void keepsGeneratedDeploymentPropertiesAndBilingualReferenceInExactAgreement() throws IOException {
        Set<String> metadataProperties = new HashSet<>();
        for (JsonNode property : readMetadata().path("properties")) {
            metadataProperties.add(property.path("name").stringValue());
        }

        Path repositoryRoot = findRepositoryRoot();
        Set<String> englishProperties = documentedProperties(repositoryRoot.resolve("docs/en/configuration.md"));
        Set<String> chineseProperties = documentedProperties(repositoryRoot.resolve("docs/zh/configuration.md"));

        assertThat(englishProperties).containsExactlyInAnyOrderElementsOf(metadataProperties);
        assertThat(chineseProperties).containsExactlyInAnyOrderElementsOf(metadataProperties);
    }
}
