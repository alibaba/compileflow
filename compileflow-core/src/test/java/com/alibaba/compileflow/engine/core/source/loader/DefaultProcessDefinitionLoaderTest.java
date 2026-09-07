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
package com.alibaba.compileflow.engine.core.source.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.config.ProcessDefinitionConfig;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.core.assembly.EngineAssembly;
import com.alibaba.compileflow.engine.core.assembly.EngineDependencies;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeRequest;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.core.source.ProcessDefinitionSnapshot;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultProcessDefinitionLoaderTest {
    @TempDir
    Path tempDirectory;

    private static URLClassLoader resourceLoader(Path root) throws Exception {
        URL rootUrl = root.toUri().toURL();
        return new URLClassLoader(new URL[] {rootUrl}, null);
    }

    private static void assertRejectedClasspathProtocol(ProcessDefinitionLoader definitionLoader, URL resource,
            String protocol) {
        assertThatThrownBy(() -> load(definitionLoader,
                ProcessRuntimeRequest.from(ProcessDefinition.classpath(ProcessModelType.TBBPM, "remote.flow",
                        "flows/test.bpm")), fixedResourceLoader(resource)))
            .isInstanceOf(CompileFlowException.ResourceException.class)
            .hasMessageContaining("must resolve to a supported local location")
            .hasMessageContaining("protocol=" + protocol);
    }

    private static ClassLoader fixedResourceLoader(URL resource) {
        return new ClassLoader(null) {
            @Override
            public URL getResource(String name) {
                return resource;
            }
        };
    }

    private static URL resourceUrl(String externalForm, String content) throws Exception {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new URL(null, externalForm, new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) {
                return new URLConnection(url) {
                    @Override
                    public void connect() {}

                    @Override
                    public InputStream getInputStream() throws IOException {
                        return new ByteArrayInputStream(bytes);
                    }
                };
            }
        });
    }

    private static ProcessDefinitionSnapshot load(ProcessDefinitionLoader loader, ProcessRuntimeRequest source,
            ClassLoader classLoader) {
        return loader.load(source, classLoader);
    }

    @Test
    void engineAssemblyLoadsClasspathFlowsFromTheConfiguredClassLoader() throws Exception {
        Path configuredRoot = createResourceRoot("configured", "configured-definition");
        Path contextRoot = createResourceRoot("context", "wrong-definition");

        try (URLClassLoader configuredLoader = resourceLoader(configuredRoot);
                URLClassLoader contextLoader = resourceLoader(contextRoot)) {
            Thread thread = Thread.currentThread();
            ClassLoader original = thread.getContextClassLoader();
            thread.setContextClassLoader(contextLoader);
            try {
                ProcessEngineConfig config =
                        ProcessEngineConfig.builder().discoverPlugins(false).classLoader(configuredLoader).build();
                EngineDependencies dependencies = EngineAssembly.assemble(config);
                try (ScriptExecutorRegistry scripts = dependencies.scriptExecutors()) {
                    assertThat(scripts.getLanguageNames()).contains("qlexpress");
                    ProcessDefinitionSnapshot source = load(dependencies.definitionLoader(),
                            ProcessRuntimeRequest.from(ProcessDefinition.classpath(ProcessModelType.TBBPM, "test.flow",
                                    "flows/test.bpm")), configuredLoader);
                    assertThat(new String(source.getBytes(), StandardCharsets.UTF_8)).isEqualTo("configured-definition");
                }
            } finally {
                thread.setContextClassLoader(original);
            }
        }
    }

    @Test
    void missingClasspathFlowReportsTheLogicalResourceName() throws Exception {
        try (URLClassLoader loader = resourceLoader(tempDirectory)) {
            ProcessDefinitionLoader definitionLoader =
                    new DefaultProcessDefinitionLoader(ProcessDefinitionConfig.defaults());

            assertThatThrownBy(() -> load(definitionLoader,
                    ProcessRuntimeRequest.from(ProcessDefinition.classpath(ProcessModelType.TBBPM, "missing.flow",
                            "flows/missing.bpm")), loader))
                .isInstanceOf(CompileFlowException.ResourceException.class)
                .hasMessageContaining("Classpath process definition not found")
                .hasMessageContaining("missing.flow")
                .hasMessageContaining("flows/missing.bpm");
        }
    }

    @Test
    void bpmnClasspathUsesTheExplicitResourcePath() throws Exception {
        Path resource = tempDirectory.resolve("flows/order.bpmn");
        Files.createDirectories(resource.getParent());
        Files.writeString(resource, "bpmn-definition");

        try (URLClassLoader loader = resourceLoader(tempDirectory)) {
            ProcessDefinitionLoader definitionLoader =
                    new DefaultProcessDefinitionLoader(ProcessDefinitionConfig.defaults());

            ProcessDefinitionSnapshot source = definitionLoader.load(ProcessRuntimeRequest.from(ProcessDefinition.classpath(ProcessModelType.BPMN,
                            "f" + "lows.order", "flows/order.bpmn")), loader);

            assertThat(source.getContent()).isEqualTo("bpmn-definition");
            assertThat(source.getModelType()).isEqualTo(ProcessModelType.BPMN);
            assertThat(source.getSourceDescription()).isEqualTo("classpath resource flows/order.bpmn");
        }
    }

    @Test
    void appliesOneByteLimitToInlineAndClasspathDefinitions() throws Exception {
        Path root = createResourceRoot("limited", "12345");
        ProcessDefinitionConfig definitionConfig = ProcessDefinitionConfig.builder().maxBytes(4).build();

        try (URLClassLoader loader = resourceLoader(root)) {
            ProcessDefinitionLoader definitionLoader = new DefaultProcessDefinitionLoader(definitionConfig);

            assertThatThrownBy(() -> load(definitionLoader,
                    ProcessRuntimeRequest.from(ProcessDefinition.inline(ProcessModelType.TBBPM, "inline.flow", "12345")),
                    loader))
                .isInstanceOf(CompileFlowException.ResourceException.class)
                .hasMessageContaining("maxBytes=4")
                .hasMessageContaining("inline.flow");
            assertThatThrownBy(() -> load(definitionLoader,
                    ProcessRuntimeRequest.from(ProcessDefinition.classpath(ProcessModelType.TBBPM, "classpath.flow",
                            "flows/test.bpm")), loader))
                .isInstanceOf(CompileFlowException.ResourceException.class)
                .hasMessageContaining("maxBytes=4")
                .hasMessageContaining("classpath.flow");
        }
    }

    @Test
    void rejectsClasspathResourcesThatResolveToNetworkLocations() throws Exception {
        URL remote = URI.create("https://example.test/flows/test.bpm").toURL();
        ClassLoader remoteLoader = new ClassLoader(null) {
            @Override
            public URL getResource(String name) {
                return remote;
            }
        };
        ProcessDefinitionLoader definitionLoader =
                new DefaultProcessDefinitionLoader(ProcessDefinitionConfig.defaults());

        assertThatThrownBy(() -> load(definitionLoader,
                ProcessRuntimeRequest.from(ProcessDefinition.classpath(ProcessModelType.TBBPM, "remote.flow",
                        "flows/test.bpm")), remoteLoader))
            .isInstanceOf(CompileFlowException.ResourceException.class)
            .hasMessageContaining("must resolve to a supported local location")
            .hasMessageContaining("protocol=https");
    }

    @Test
    void rejectsUnknownAndNestedNetworkClasspathProtocols() throws Exception {
        ProcessDefinitionLoader definitionLoader =
                new DefaultProcessDefinitionLoader(ProcessDefinitionConfig.defaults());

        assertRejectedClasspathProtocol(definitionLoader, resourceUrl("sftp://example.test/flows/test.bpm", "ignored"),
                "sftp");
        assertRejectedClasspathProtocol(definitionLoader,
                resourceUrl("jar:https://example.test/flows.jar!/flows/test.bpm", "ignored"), "https");
    }

    @Test
    void acceptsSpringBootNestedJarClasspathLocations() throws Exception {
        ProcessDefinitionLoader definitionLoader =
                new DefaultProcessDefinitionLoader(ProcessDefinitionConfig.defaults());
        URL nested = resourceUrl("jar:nested:/opt/app.jar/!BOOT-INF/classes/!/flows/test.bpm", "nested-definition");
        ClassLoader nestedLoader = fixedResourceLoader(nested);

        ProcessDefinitionSnapshot resolved = load(definitionLoader,
                ProcessRuntimeRequest.from(ProcessDefinition.classpath(ProcessModelType.TBBPM, "nested.flow",
                        "flows/test.bpm")), nestedLoader);

        assertThat(resolved.getContent()).isEqualTo("nested-definition");
    }

    @Test
    void resolvesClasspathContentAsOneStrictUtf8Snapshot() throws Exception {
        Path root = Files.createDirectories(tempDirectory.resolve("snapshots"));
        Path sourceFile = root.resolve("flows/flow.bpm");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, "first", StandardCharsets.UTF_8);
        ProcessDefinitionLoader definitionLoader =
                new DefaultProcessDefinitionLoader(ProcessDefinitionConfig.defaults());
        ProcessRuntimeRequest source = ProcessRuntimeRequest.versioned(ProcessRef.version("orders", "snapshot.flow", "7"),
                ProcessDefinition.classpath(ProcessModelType.TBBPM, "snapshot.flow", "flows/flow.bpm"));

        try (URLClassLoader classLoader = resourceLoader(root)) {
            ProcessDefinitionSnapshot first = load(definitionLoader, source, classLoader);
            Files.writeString(sourceFile, "second", StandardCharsets.UTF_8);
            ProcessDefinitionSnapshot second = load(definitionLoader, source, classLoader);

            assertThat(first.getContent()).isEqualTo("first");
            assertThat(second.getContent()).isEqualTo("second");
            assertThat(first.getNamespace()).isEqualTo("orders");
            assertThat(first.getVersion()).isEqualTo("7");
            assertThat(first.getSourceDigest()).isNotEqualTo(second.getSourceDigest());
            assertThat(first.getSourceDescription()).isEqualTo("classpath resource flows/flow.bpm");
        }
    }

    @Test
    void rejectsMalformedUtf8BeforeParsing() throws Exception {
        Path root = Files.createDirectories(tempDirectory.resolve("invalid-utf8"));
        Path sourceFile = root.resolve("flows/flow.bpm");
        Files.createDirectories(sourceFile.getParent());
        Files.write(sourceFile, new byte[] {(byte) 0xC3, 0x28});
        ProcessDefinitionLoader definitionLoader =
                new DefaultProcessDefinitionLoader(ProcessDefinitionConfig.defaults());

        try (URLClassLoader classLoader = resourceLoader(root)) {
            assertThatThrownBy(() -> load(definitionLoader,
                    ProcessRuntimeRequest.from(ProcessDefinition.classpath(ProcessModelType.TBBPM, "invalid.flow",
                            "flows/flow.bpm")), classLoader))
                .isInstanceOf(CompileFlowException.ResourceException.class)
                .hasMessageContaining("not valid UTF-8")
                .hasMessageContaining("invalid.flow");
        }
    }

    private Path createResourceRoot(String directory, String content) throws Exception {
        Path root = Files.createDirectories(tempDirectory.resolve(directory));
        Path resource = root.resolve("flows/test.bpm");
        Files.createDirectories(resource.getParent());
        Files.writeString(resource, content, StandardCharsets.UTF_8);
        return root;
    }
}
