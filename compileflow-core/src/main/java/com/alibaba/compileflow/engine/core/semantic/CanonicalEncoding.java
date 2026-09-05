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
package com.alibaba.compileflow.engine.core.semantic;

import com.alibaba.compileflow.engine.ProcessText;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Unambiguous field encoding shared by deterministic semantic digests.
 *
 * @author yusu
 */
public final class CanonicalEncoding {
    private CanonicalEncoding() {
    }

    /**
     * Appends one length-prefixed field, using a negative length exclusively for {@code null}.
     */
    public static void append(StringBuilder target, String name, String value) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(name, "name");
        target.append(name.length()).append(':').append(name).append('=');
        if (value == null) {
            target.append(-1).append(':');
        } else {
            target.append(value.length()).append(':').append(value);
        }
        target.append('\n');
    }

    /**
     * Returns the lowercase SHA-256 digest of the exact UTF-8 source.
     */
    public static String sha256(String source) {
        Objects.requireNonNull(source, "source");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(ProcessText.encodeUtf8(source, "Canonical source")));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", impossible);
        }
    }
}
