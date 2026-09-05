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
package com.alibaba.compileflow.workbench.server.config;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class CompileFlowWorkbenchServerConfigurationMetadataTest {
    private static final String METADATA = "META-INF/spring-configuration-metadata.json";
    private static final Pattern DOCUMENTED_PROPERTY =
            Pattern.compile("(?m)^[|]\\s*`(compileflow[.]workbench[.]server[.][a-z0-9]+(?:[.-][a-z0-9]+)*)`\\s*[|]");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static JsonNode readMetadata() throws IOException {
        Enumeration<URL> resources =
                CompileFlowWorkbenchServerConfigurationMetadataTest.class.getClassLoader().getResources(METADATA);
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            try (InputStream input = resource.openStream()) {
                JsonNode metadata = OBJECT_MAPPER.readTree(input);
                for (JsonNode group : metadata.path("groups")) {
                    if ("compileflow.workbench.server".equals(group.path("name").stringValue())) {
                        return metadata;
                    }
                }
            }
        }
        throw new AssertionError("Cannot locate Workbench " + METADATA + " on the test classpath");
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
    void shouldPublishDocumentedServerConfigurationMetadata() throws IOException {
        JsonNode root = readMetadata();

        Set<String> names = new HashSet<>();
        Map<String, JsonNode> properties = new HashMap<>();
        for (JsonNode property : root.path("properties")) {
            String name = property.path("name").stringValue();
            assertThat(name).startsWith("compileflow.workbench.server.");
            assertThat(property.path("description").stringValue()).as(name).isNotBlank();
            assertThat(names.add(name)).as("duplicate metadata property %s", name).isTrue();
            properties.put(name, property);
        }

        assertThat(properties
            .get("compileflow.workbench.server.async-invocation.concurrency")
            .path("defaultValue")
            .asInt())
            .isEqualTo(4);
        assertThat(properties
            .get("compileflow.workbench.server.async-invocation.lease-duration")
            .path("defaultValue")
            .stringValue())
            .isEqualTo("30s");
        assertThat(properties
            .get("compileflow.workbench.server.authentication.mode")
            .path("defaultValue")
            .stringValue())
            .isEqualTo("API_KEY");
        assertThat(properties
            .get("compileflow.workbench.server.database.migrate")
            .path("defaultValue")
            .booleanValue())
            .isFalse();
        assertThat(properties.get("compileflow.workbench.server.authentication.api-key").has("defaultValue"))
            .as("a conditionally required credential must not advertise an empty default")
            .isFalse();
        assertThat(properties.get("compileflow.workbench.server.authentication.service-principal").has("defaultValue"))
            .as("a required identity must not advertise an empty default")
            .isFalse();
        JsonNode previewEnabled = properties.get("compileflow.workbench.server.preview-execution.enabled");
        assertThat(previewEnabled.has("defaultValue")).isTrue();
        assertThat(previewEnabled.path("defaultValue").isBoolean()).isTrue();
        assertThat(previewEnabled.path("defaultValue").booleanValue()).isFalse();

        Set<String> authenticationModes = new HashSet<>();
        for (JsonNode hint : root.path("hints")) {
            if (!"compileflow.workbench.server.authentication.mode".equals(hint.path("name").stringValue())) {
                continue;
            }
            for (JsonNode value : hint.path("values")) {
                assertThat(value.path("description").stringValue()).isNotBlank();
                authenticationModes.add(value.path("value").stringValue());
            }
        }
        assertThat(authenticationModes).containsExactlyInAnyOrder("API_KEY", "DISABLED");

        assertThat(properties).doesNotContainKey("compileflow.workbench.server.api-key");
        assertThat(properties).doesNotContainKey("compileflow.workbench.server.async-invocation.lease-renewal-interval");
        assertThat(properties.keySet()).noneMatch(name -> name.startsWith("compileflow.workbench.server.cors."));
        assertThat(properties
            .entrySet()
            .stream()
            .filter(entry -> !entry.getValue().has("defaultValue"))
            .map(Map.Entry::getKey)
            .toList())
            .containsExactlyInAnyOrder("compileflow.workbench.server.authentication.api-key",
                    "compileflow.workbench.server.authentication.service-principal");
    }

    @Test
    void shouldKeepGeneratedPropertiesAndBilingualReferenceInExactAgreement() throws IOException {
        Set<String> metadataProperties = new HashSet<>();
        for (JsonNode property : readMetadata().path("properties")) {
            String name = property.path("name").stringValue();
            if (name.startsWith("compileflow.workbench.server.")) {
                metadataProperties.add(name);
            }
        }

        Path repositoryRoot = findRepositoryRoot();
        assertThat(documentedProperties(repositoryRoot.resolve("docs/en/configuration.md")))
            .containsExactlyInAnyOrderElementsOf(metadataProperties);
        assertThat(documentedProperties(repositoryRoot.resolve("docs/zh/configuration.md")))
            .containsExactlyInAnyOrderElementsOf(metadataProperties);
    }
}
