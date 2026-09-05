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
package com.alibaba.compileflow.deploy.api.release;

import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.ProcessText;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded immutable representation of deployment release metadata.
 *
 * @author yusu
 */
public final class ReleaseMetadata {
    public static final int MAX_ENTRIES = 64;
    public static final int MAX_KEY_CHARACTERS = 128;
    public static final int MAX_VALUE_CHARACTERS = 2_048;
    public static final int MAX_UTF8_BYTES = 32 * 1_024;

    private ReleaseMetadata() {
    }

    /**
     * Creates a bounded immutable metadata snapshot.
     *
     * @param source source metadata, or {@code null} for an empty snapshot
     * @return immutable validated metadata
     */
    public static Map<String, String> immutableCopy(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        if (source.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("metadata must not contain more than " + MAX_ENTRIES + " entries");
        }
        Map<String, String> copy = new LinkedHashMap<>();
        long utf8Bytes = 0L;
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String key = ProcessIdentifiers.requireExactIdentity(entry.getKey(), "metadata key", MAX_KEY_CHARACTERS);
            if (key.startsWith(ReleaseMetadataKeys.RESERVED_PREFIX) && !ReleaseMetadataKeys.isSupportedReservedKey(key)) {
                throw new IllegalArgumentException(
                        "metadata key uses the reserved " + ReleaseMetadataKeys.RESERVED_PREFIX + " namespace");
            }
            String value = entry.getValue();
            if (value == null) {
                throw new IllegalArgumentException("metadata value must not be null");
            }
            ProcessText.requireUnicode(value, "metadata value");
            requireMaximumCharacters(value, "metadata value", MAX_VALUE_CHARACTERS);
            utf8Bytes += key.getBytes(StandardCharsets.UTF_8).length;
            utf8Bytes += value.getBytes(StandardCharsets.UTF_8).length;
            if (utf8Bytes > MAX_UTF8_BYTES) {
                throw new IllegalArgumentException("metadata must not exceed " + MAX_UTF8_BYTES + " UTF-8 bytes");
            }
            copy.put(key, value);
        }
        return Collections.unmodifiableMap(copy);
    }

    private static void requireMaximumCharacters(String value, String name, int maximumCharacters) {
        if (value.codePointCount(0, value.length()) > maximumCharacters) {
            throw new IllegalArgumentException(name + " must not exceed " + maximumCharacters + " characters");
        }
    }
}
