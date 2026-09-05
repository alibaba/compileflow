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
package com.alibaba.compileflow.engine.core.java.naming;

import com.alibaba.compileflow.engine.core.semantic.naming.ProcessNames;
import org.apache.commons.lang3.StringUtils;

/**
 * Java interoperation naming rules that are independent of code generation.
 *
 * @author yusu
 */
public final class JavaNames {
    private static final int MAX_CLASS_NAME_LENGTH = 500;

    private JavaNames() {
    }

    public static boolean isIdentifier(String value) {
        return ProcessNames.isIdentifier(value);
    }

    public static boolean isClassName(String className) {
        if (StringUtils.isBlank(className) || className.length() > MAX_CLASS_NAME_LENGTH || className.indexOf('/') >= 0
                || className.indexOf('\\') >= 0) {
            return false;
        }
        String[] segments = className.split("\\.", -1);
        for (String segment : segments) {
            if (!isIdentifier(segment)) {
                return false;
            }
        }
        return true;
    }

    public static String requireValidClassName(String className) {
        if (!isClassName(className)) {
            throw new IllegalArgumentException("Invalid Java class name: " + className);
        }
        return className;
    }
}
