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
package com.alibaba.compileflow.engine.core.semantic.plan;

import static com.alibaba.compileflow.engine.core.semantic.SemanticText.requireIdentity;
import com.alibaba.compileflow.engine.ProcessIdentifiers;

/**
 * Exact target selector declared by one Process call site.
 *
 * @author yusu
 */
public sealed interface ProcessCallTarget permits ProcessCallTarget.Classpath, ProcessCallTarget.Version {
    /**
     * Creates the target selected by the protocol attributes.
     */
    static ProcessCallTarget from(String classpath, String version) {
        if ((classpath == null) == (version == null)) {
            throw new IllegalArgumentException("exactly one of classpath or version is required");
        }
        return classpath != null ? new Classpath(classpath) : new Version(version);
    }

    /**
     * Calls a definition at one explicit classpath location.
     */
    record Classpath(String resourcePath) implements ProcessCallTarget {
        public Classpath {
            String path = requireIdentity(resourcePath, "resourcePath");
            if (path.startsWith("/") || path.contains("\\") || path.contains("*") || path.contains(":")) {
                throw new IllegalArgumentException("resourcePath must be a plain relative classpath location");
            }
            for (String segment : path.split("/", -1)) {
                if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                    throw new IllegalArgumentException("resourcePath must contain only canonical path segments");
                }
            }
            resourcePath = path;
        }
    }

    /**
     * Calls one immutable published Process Version.
     */
    record Version(String version) implements ProcessCallTarget {
        public Version {
            version = ProcessIdentifiers.requireVersion(version);
        }
    }
}
