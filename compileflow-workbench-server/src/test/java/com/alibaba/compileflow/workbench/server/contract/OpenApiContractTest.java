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
package com.alibaba.compileflow.workbench.server.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {"springdoc.api-docs.enabled=true", "springdoc.api-docs.version=OPENAPI_3_1",
        "springdoc.paths-to-match=/api/**",
        "springdoc.default-consumes-media-type=application/json",
        "springdoc.default-produces-media-type=application/json"})
@ActiveProfiles("test")
@Import(OpenApiContractTest.ContractConfiguration.class)
class OpenApiContractTest {
    private static final String UPDATE_PROPERTY = "compileflow.openapi.update";
    private static final String CONTRACT_PATH = "docs/specs/openapi/compileflow-workbench-server.openapi.json";
    private static final Pattern OPERATION_ID_PATTERN =
            Pattern.compile("operationId[^A-Za-z0-9]+([A-Za-z][A-Za-z0-9_]*)");
    private static final ObjectMapper CANONICAL_MAPPER = JsonMapper
        .builder()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
        .build();
    @Autowired
    private WebApplicationContext applicationContext;
    private MockMvc mockMvc;

    private static String canonicalize(String json) throws Exception {
        Object value = CANONICAL_MAPPER.readValue(json, Object.class);
        return CANONICAL_MAPPER.writeValueAsString(value) + System.lineSeparator();
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("compileflow-workbench-server"))
                    && Files.isDirectory(current.resolve("compileflow-workbench"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("CompileFlow repository root was not found");
    }

    private static List<String> operationIds(String openApiJson) {
        List<String> result = new ArrayList<>();
        Matcher matcher = OPERATION_ID_PATTERN.matcher(openApiJson);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
    }

    @Test
    void generatedDescriptionMatchesCommittedContract() throws Exception {
        String generated = mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
        String canonical = canonicalize(generated);
        Path contract = repositoryRoot().resolve(CONTRACT_PATH);
        List<String> operationIds = operationIds(canonical);

        assertThat(operationIds)
            .isNotEmpty()
            .doesNotHaveDuplicates()
            .doesNotContain("get", "list", "execute", "export", "health", "status")
            .allSatisfy(operationId -> assertThat(operationId).doesNotMatch(".*_\\d+$"));

        if (Boolean.getBoolean(UPDATE_PROPERTY)) {
            Files.createDirectories(contract.getParent());
            Files.writeString(contract, canonical, StandardCharsets.UTF_8);
        }

        assertThat(contract).as("Run with -D%s=true to regenerate %s", UPDATE_PROPERTY, CONTRACT_PATH).isRegularFile();
        assertThat(canonical).isEqualTo(Files.readString(contract, StandardCharsets.UTF_8));
    }

    @Test
    void missingRequiredIdempotencyHeaderUsesProblemContract() throws Exception {
        mockMvc
            .perform(post("/api/deployments").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.detail").value("Idempotency-Key is required"));
    }

    @ParameterizedTest
    @CsvSource({"/api/execution-logs/export,post,text/csv", "/api/processes/{code}/export,get,application/xml"})
    void exportsDescribeRawDownloadsRatherThanBase64(String path, String method, String mediaType) throws Exception {
        String generated = mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
        var schema = CANONICAL_MAPPER
            .readTree(generated)
            .path("paths")
            .path(path)
            .path(method)
            .path("responses")
            .path("200")
            .path("content")
            .path(mediaType)
            .path("schema");

        assertThat(schema.isMissingNode()).as("Download schema for %s %s", method, path).isFalse();
        assertThat(schema.path("type").stringValue()).isEqualTo("string");
        assertThat(schema.path("format").stringValue()).isEqualTo("binary");
        assertThat(schema.has("contentEncoding")).as("Raw downloads must not require base64 decoding").isFalse();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ContractConfiguration {
        @Bean
        OpenAPI compileProcessOpenApi() {
            return new OpenAPI()
                .info(new Info()
                    .title("CompileFlow Workbench Server API")
                    .version("2.0.0")
                    .license(new License()
                        .name("Apache License 2.0")
                        .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .addServersItem(new Server().url("/").description("Current Workbench origin"))
                .components(new Components()
                    .addSecuritySchemes("ApiKeyAuth",
                            new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name(
                                    "X-API-Key")))
                .addSecurityItem(new SecurityRequirement().addList("ApiKeyAuth"));
        }
    }
}
