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
package com.alibaba.compileflow.engine;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Supplies or locates a process definition without carrying namespace, version, alias, or model
 * type.
 *
 * <p>The receiving execution configuration determines the model type. Inline content is
 * intentionally redacted from string representations.
 *
 * @author yusu
 */
public sealed interface ProcessDefinition permits ProcessDefinition.Inline, ProcessDefinition.Classpath {
    /**
     * Creates an inline definition.
     *
     * @param code process code
     * @param content process-definition content
     * @return inline definition
     */
    static Inline inline(String code, String content) {
        return new Inline(code, content);
    }

    /**
     * Creates a classpath-backed definition.
     *
     * @param code process code
     * @param resourcePath classpath resource name
     * @return classpath-backed definition
     */
    static Classpath classpath(String code, String resourcePath) {
        return new Classpath(code, resourcePath);
    }

    private static String requireCode(String value) {
        return ProcessIdentifiers.requireCode(value);
    }

    private static String normalizeResourcePath(String value) {
        String resource = Objects.requireNonNull(value, "resourcePath").replace('\\', '/');
        while (resource.startsWith("/")) {
            resource = resource.substring(1);
        }
        Deque<String> segments = new ArrayDeque<>();
        for (String segment : resource.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                throw new IllegalArgumentException("resourcePath must not contain parent traversal");
            }
            segments.addLast(segment);
        }
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("resourcePath must not be blank");
        }
        StringJoiner normalized = new StringJoiner("/");
        segments.forEach(normalized::add);
        return normalized.toString();
    }

    /**
     * Returns the process code declared for this definition.
     *
     * @return validated process code unchanged
     */
    String code();

    /**
     * Carries inline process-definition content.
     *
     * @param code process code
     * @param content process-definition content
     */
    record Inline(String code, String content) implements ProcessDefinition {
        public Inline {
            code = requireCode(code);
            content = Objects.requireNonNull(content, "content");
            ProcessText.requireNonBlank(content, "content");
        }

        @Override
        public String toString() {
            return "ProcessDefinition.Inline{code='" + code + "', content=<redacted>}";
        }
    }

    /**
     * References a process definition on the engine classpath.
     *
     * @param code process code
     * @param resourcePath normalized classpath resource name
     */
    record Classpath(String code, String resourcePath) implements ProcessDefinition {
        public Classpath {
            code = requireCode(code);
            resourcePath = normalizeResourcePath(resourcePath);
        }

        @Override
        public String toString() {
            return "ProcessDefinition.Classpath{code='" + code + "', resource='" + resourcePath + "'}";
        }
    }
}
