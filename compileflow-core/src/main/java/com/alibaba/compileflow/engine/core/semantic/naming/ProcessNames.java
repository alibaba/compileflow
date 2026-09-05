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
package com.alibaba.compileflow.engine.core.semantic.naming;

import java.util.Set;
import javax.lang.model.SourceVersion;
import org.apache.commons.lang3.StringUtils;

/**
 * Process-level naming policy shared by source formats and execution targets.
 *
 * <p>CompileFlow deliberately keeps process variable and mapping names Java-compatible so
 * compiled and interpreted targets expose the same definition contract. This class owns that
 * Process rule; code generators only adapt already-valid names to generated symbols.</p>
 *
 * @author yusu
 */
public final class ProcessNames {
    private static final Set<String> RESTRICTED_IDENTIFIERS = Set.of("record", "sealed", "permits", "var", "yield");

    private ProcessNames() {
    }

    public static boolean isIdentifier(String value) {
        if (StringUtils.isBlank(value)) {
            return false;
        }
        if (!SourceVersion.isIdentifier(value) || SourceVersion.isKeyword(value, SourceVersion.RELEASE_17)
                || RESTRICTED_IDENTIFIERS.contains(value)) {
            return false;
        }
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if (Character.isIdentifierIgnorable(codePoint)) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    public static boolean isReserved(String value) {
        return value != null && (value.startsWith("_cf$") || value.startsWith("__cf_"));
    }

    public static boolean isJavaKeyword(String value) {
        return value != null
                && (SourceVersion.isKeyword(value, SourceVersion.RELEASE_17) || RESTRICTED_IDENTIFIERS.contains(value));
    }
}
