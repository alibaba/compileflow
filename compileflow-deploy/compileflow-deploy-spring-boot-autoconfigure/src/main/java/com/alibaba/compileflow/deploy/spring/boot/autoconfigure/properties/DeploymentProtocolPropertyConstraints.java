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
package com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Deployment protocol shape checks that remain safe when Deploy is absent.
 *
 * <p>The auto-configuration artifact declares Deploy as optional. Configuration
 * binding is unconditional, so its value objects cannot link Deploy API classes.
 * Contract tests keep these admission checks aligned with the canonical protocol.
 *
 * @author yusu
 */
final class DeploymentProtocolPropertyConstraints {
    private static final int MAX_PREFIX_BYTES = 128;
    private static final String ALIAS_MARKER = "alias.";
    private static final Pattern PORTABLE_PREFIX = Pattern.compile("[A-Za-z0-9._:-]+");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private DeploymentProtocolPropertyConstraints() {
    }

    static boolean isPortableKeyPrefix(String value) {
        return value != null && !value.isBlank() && value.equals(value.trim())
                && PORTABLE_PREFIX.matcher(value).matches()
                && value.getBytes(StandardCharsets.UTF_8).length <= MAX_PREFIX_BYTES;
    }

    static boolean isAliasStateKey(String value) {
        if (value == null) {
            return false;
        }
        int marker = value.lastIndexOf(ALIAS_MARKER);
        if (marker <= 0) {
            return false;
        }
        String prefix = value.substring(0, marker);
        String digest = value.substring(marker + ALIAS_MARKER.length());
        return isPortableKeyPrefix(prefix) && SHA_256.matcher(digest).matches();
    }
}
