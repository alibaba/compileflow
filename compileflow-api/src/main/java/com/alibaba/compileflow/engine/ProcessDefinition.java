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

import java.util.Objects;
import java.util.StringJoiner;

/**
 * Supplies or locates a typed process definition without carrying namespace, version, or alias.
 *
 * <p>The definition declares its semantic format. Inline content is intentionally redacted
 * from string representations.
 *
 * @author yusu
 */
public sealed interface ProcessDefinition permits ProcessDefinition.Inline, ProcessDefinition.Classpath {
    /**
     * Creates an inline definition.
     *
     * @param modelType process definition format
     * @param code process code
     * @param content process-definition content
     * @return inline definition
     */
    static Inline inline(ProcessModelType modelType, String code, String content) {
        return new Inline(modelType, code, content);
    }

    /**
     * Creates a classpath-backed definition.
     *
     * @param modelType process definition format
     * @param code process code
     * @param resourcePath classpath resource name
     * @return classpath-backed definition
     */
    static Classpath classpath(ProcessModelType modelType, String code, String resourcePath) {
        return new Classpath(modelType, code, resourcePath);
    }

    private static String normalizeResourcePath(String value) {
        String resource = Objects.requireNonNull(value, "resourcePath").replace('\\', '/');
        StringJoiner normalized = new StringJoiner("/");
        for (String segment : resource.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                throw new IllegalArgumentException("resourcePath must not contain parent traversal");
            }
            normalized.add(segment);
        }
        return ProcessText.requireNonBlank(normalized.toString(), "resourcePath");
    }

    /**
     * Returns the process code declared for this definition.
     *
     * @return validated process code unchanged
     */
    String code();

    /**
     * Returns the explicitly declared semantic format.
     * @return process definition format
     */
    ProcessModelType modelType();

    /**
     * Carries inline process-definition content.
     *
     * @param modelType process definition format
     * @param code process code
     * @param content process-definition content
     */
    record Inline(ProcessModelType modelType, String code, String content) implements ProcessDefinition {
        public Inline {
            modelType = Objects.requireNonNull(modelType, "modelType");
            code = ProcessIdentifiers.requireCode(code);
            content = ProcessText.requireNonBlank(content, "content");
        }

        @Override
        public String toString() {
            return "ProcessDefinition.Inline{modelType=" + modelType + ", code='" + code + "', content=<redacted>}";
        }
    }

    /**
     * References a process definition on the engine classpath.
     *
     * @param modelType process definition format
     * @param code process code
     * @param resourcePath normalized classpath resource name
     */
    record Classpath(ProcessModelType modelType, String code, String resourcePath) implements ProcessDefinition {
        public Classpath {
            modelType = Objects.requireNonNull(modelType, "modelType");
            code = ProcessIdentifiers.requireCode(code);
            resourcePath = normalizeResourcePath(resourcePath);
        }

        @Override
        public String toString() {
            return "ProcessDefinition.Classpath{modelType=" + modelType + ", code='" + code + "', resource='"
                    + resourcePath + "'}";
        }
    }
}
