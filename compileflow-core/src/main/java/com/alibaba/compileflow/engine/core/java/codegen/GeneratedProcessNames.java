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
package com.alibaba.compileflow.engine.core.java.codegen;

import org.apache.commons.lang3.StringUtils;

/**
 * Naming helpers for generated process classes.
 *
 * @author yusu
 */
final class GeneratedProcessNames {
    private static final String GENERATED_PROCESS_PACKAGE = "com.alibaba.compileflow.generated.process";

    private GeneratedProcessNames() {
    }

    static String className(String processCode) {
        if (processCode == null) {
            throw new IllegalArgumentException("Process code cannot be null.");
        }
        int lastDotIndex = processCode.lastIndexOf('.');
        String basePackage = lastDotIndex > 0 ? processCode.substring(0, lastDotIndex) : "";
        String simpleName = lastDotIndex > 0 ? processCode.substring(lastDotIndex + 1) : processCode;
        String className = simpleClassName(simpleName);

        String safePackage = StringUtils.isEmpty(basePackage) ? "" : JavaIdentifiers.toPackageName(basePackage);
        String qualifiedName = StringUtils.isEmpty(safePackage) ? className : safePackage + "." + className;
        return GENERATED_PROCESS_PACKAGE + "." + qualifiedName;
    }

    private static String simpleClassName(String processCode) {
        String className = JavaIdentifiers.toClassName(processCode);
        if (!className.endsWith("Flow")) {
            className = className + "Flow";
        }
        return className;
    }
}
