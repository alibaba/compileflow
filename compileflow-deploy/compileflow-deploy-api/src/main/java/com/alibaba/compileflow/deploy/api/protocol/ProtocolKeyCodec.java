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
package com.alibaba.compileflow.deploy.api.protocol;

import com.alibaba.compileflow.engine.ProcessIdentifiers;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Canonical bounded key derivation for deployment protocol identities.
 *
 * @author yusu
 */
public final class ProtocolKeyCodec {
    private static final int MAX_PREFIX_BYTES = 128;

    private ProtocolKeyCodec() {
    }

    /**
     * Digests an ordered identity tuple using length-prefixed UTF-8 segments.
     *
     * @param segments ordered non-null identity segments
     * @return lowercase SHA-256 hexadecimal identity
     */
    public static String digestIdentity(String... segments) {
        Objects.requireNonNull(segments, "segments");
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
        for (String segment : segments) {
            byte[] bytes = ProcessIdentifiers
                .requireExactIdentity(segment, "identity segment", Integer.MAX_VALUE)
                .getBytes(StandardCharsets.UTF_8);
            int length = bytes.length;
            digest.update((byte) (length >>> 24));
            digest.update((byte) (length >>> 16));
            digest.update((byte) (length >>> 8));
            digest.update((byte) length);
            digest.update(bytes);
        }
        byte[] hash = digest.digest();
        StringBuilder hexadecimal = new StringBuilder(hash.length * 2);
        for (byte value : hash) {
            hexadecimal.append(Character.forDigit((value >>> 4) & 0x0f, 16));
            hexadecimal.append(Character.forDigit(value & 0x0f, 16));
        }
        return hexadecimal.toString();
    }

    /**
     * Returns a portable deployment key prefix.
     *
     * @param configured    configured prefix, or blank to select {@code defaultPrefix}
     * @param defaultPrefix non-blank protocol default
     * @return validated transport-neutral prefix
     */
    public static String keyPrefix(String configured, String defaultPrefix) {
        String prefix = configured == null || configured.isBlank()
                ? Objects.requireNonNull(defaultPrefix, "defaultPrefix")
                : configured;
        if (!prefix.equals(prefix.trim())) {
            throw new IllegalArgumentException("keyPrefix must not contain surrounding whitespace");
        }
        if (!prefix.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException(
                    "keyPrefix must contain only ASCII letters, digits, '.', '_', ':', or '-'");
        }
        if (prefix.getBytes(StandardCharsets.UTF_8).length > MAX_PREFIX_BYTES) {
            throw new IllegalArgumentException("keyPrefix must not exceed " + MAX_PREFIX_BYTES + " UTF-8 bytes");
        }
        return prefix;
    }
}
