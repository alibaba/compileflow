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

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.config.ProcessDefinitionConfig;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeRequest;
import com.alibaba.compileflow.engine.core.source.ProcessDefinitionSnapshot;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Default {@link ProcessDefinitionLoader} for inline and classpath definitions.
 *
 * @author yusu
 */
public final class DefaultProcessDefinitionLoader implements ProcessDefinitionLoader {
    private final ProcessDefinitionConfig definitionConfig;

    public DefaultProcessDefinitionLoader(ProcessDefinitionConfig definitionConfig) {
        this.definitionConfig = Objects.requireNonNull(definitionConfig, "definitionConfig");
    }

    private static void requireLocalClasspathLocation(String code, String resource, URL location) {
        String rejectedProtocol = rejectedClasspathProtocol(location.toExternalForm());
        if (rejectedProtocol != null) {
            throw new CompileFlowException.ResourceException(ErrorCode.CF_RESOURCE_003,
                    "Classpath process definitions must resolve to a supported local location: code=" + code
                    + ", resource=" + resource + ", protocol=" + rejectedProtocol, null);
        }
    }

    private static String rejectedClasspathProtocol(String externalForm) {
        String location = externalForm.toLowerCase(Locale.ROOT);
        if (location.startsWith("file:") || location.startsWith("jrt:")) {
            return null;
        }
        if (location.startsWith("nested:/") && !location.startsWith("nested://")) {
            return null;
        }
        if (location.startsWith("jar:")) {
            return rejectedClasspathProtocol(location.substring("jar:".length()));
        }
        int separator = location.indexOf(':');
        return separator <= 0 ? "<unknown>" : location.substring(0, separator);
    }

    @Override
    public ProcessDefinitionSnapshot load(ProcessRuntimeRequest request, ClassLoader classLoader) {
        ProcessRuntimeRequest runtimeRequest = Objects.requireNonNull(request, "request");
        ClassLoader lookupClassLoader = Objects.requireNonNull(classLoader, "classLoader");
        String code = runtimeRequest.getCode();
        DefinitionContent content = loadContent(runtimeRequest, lookupClassLoader);
        try {
            return ProcessDefinitionSnapshot.of(runtimeRequest.getDefinition().modelType(),
                    runtimeRequest.getNamespace(), code, runtimeRequest.getVersion(), content.bytes(),
                    content.description());
        } catch (IllegalArgumentException invalidUtf8) {
            throw new CompileFlowException.ResourceException(ErrorCode.CF_RESOURCE_002,
                    "Process definition is not valid UTF-8: code=" + code + ", source=" + content.description(),
                    invalidUtf8);
        }
    }

    private DefinitionContent loadContent(ProcessRuntimeRequest request, ClassLoader classLoader) {
        String code = request.getCode();
        ProcessDefinition definition = request.getDefinition();
        if (definition instanceof ProcessDefinition.Inline inline) {
            return new DefinitionContent(snapshotInline(code, inline.content()), "inline content");
        }
        if (definition instanceof ProcessDefinition.Classpath classpath) {
            String resource = classpath.resourcePath();
            return new DefinitionContent(snapshotClasspath(code, resource, classLoader),
                    "classpath resource " + resource);
        }
        throw new IllegalArgumentException(
                "Runtime request does not contain a resolvable process definition: " + request);
    }

    private byte[] snapshotInline(String code, String content) {
        if (content.length() > definitionConfig.getMaxBytes()) {
            requireWithinLimit(code, "inline content", content.length());
        }
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        requireWithinLimit(code, "inline content", bytes.length);
        return bytes;
    }

    private record DefinitionContent(byte[] bytes, String description) {}

    private byte[] snapshotClasspath(String code, String resource, ClassLoader classLoader) {
        List<URL> locations = classpathLocations(code, resource, classLoader);
        if (locations.isEmpty()) {
            throw new CompileFlowException.ResourceException(ErrorCode.CF_RESOURCE_001,
                    "Classpath process definition not found: code=" + code + ", resource=" + resource, null);
        }
        byte[] selected = null;
        for (URL location : locations) {
            requireLocalClasspathLocation(code, resource, location);
            try (InputStream input = location.openStream()) {
                byte[] candidate = readWithinLimit(code, "classpath resource " + resource, input);
                if (selected == null) {
                    selected = candidate;
                } else if (!Arrays.equals(selected, candidate)) {
                    throw new CompileFlowException.ResourceException(ErrorCode.CF_RESOURCE_003,
                            "Classpath process definition is ambiguous: code=" + code + ", resource=" + resource
                            + ", locations=" + locations.size(), null);
                }
            } catch (CompileFlowException exception) {
                throw exception;
            } catch (IOException exception) {
                throw new CompileFlowException.ResourceException(ErrorCode.CF_RESOURCE_002,
                        "Failed to read classpath process definition: code=" + code + ", resource=" + resource,
                        exception);
            }
        }
        return Objects.requireNonNull(selected, "selected");
    }

    private static List<URL> classpathLocations(String code, String resource, ClassLoader classLoader) {
        try {
            Enumeration<URL> resources = classLoader.getResources(resource);
            List<URL> locations = new ArrayList<>();
            while (resources.hasMoreElements()) {
                locations.add(resources.nextElement());
            }
            if (locations.isEmpty()) {
                URL single = classLoader.getResource(resource);
                if (single != null) {
                    locations.add(single);
                }
            }
            locations.sort(java.util.Comparator.comparing(URL::toExternalForm));
            return locations;
        } catch (IOException exception) {
            throw new CompileFlowException.ResourceException(ErrorCode.CF_RESOURCE_002,
                    "Failed to locate classpath process definition: code=" + code + ", resource=" + resource, exception);
        }
    }

    private byte[] readWithinLimit(String code, String source, InputStream input) throws IOException {
        int maxBytes = definitionConfig.getMaxBytes();
        byte[] bytes = input.readNBytes(maxBytes + 1);
        requireWithinLimit(code, source, bytes.length);
        return bytes;
    }

    private void requireWithinLimit(String code, String source, long actualBytes) {
        int maxBytes = definitionConfig.getMaxBytes();
        if (actualBytes > maxBytes) {
            throw new CompileFlowException.ResourceException(ErrorCode.CF_RESOURCE_003,
                    "Process definition exceeds the configured size limit: code=" + code + ", source=" + source
                    + ", maxBytes=" + maxBytes + ", observedBytes=" + actualBytes, null);
        }
    }
}
