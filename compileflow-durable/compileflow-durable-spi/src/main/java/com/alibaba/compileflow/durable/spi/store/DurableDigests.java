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
package com.alibaba.compileflow.durable.spi.store;

import com.alibaba.compileflow.engine.ProcessText;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Canonical SHA-256 calculations used by Durable protocol identities.
 *
 * <p>Structured identities must use {@link #sha256Fields(String, String...)}
 * rather than concatenate fields. Its length-prefixed representation keeps
 * field boundaries unambiguous across implementations.</p>
 *
 * @author yusu
 */
public final class DurableDigests {
    private static final String FIELD_FORMAT = "compileflow.length-prefixed-fields/v1;";

    private DurableDigests() {
    }

    /**
     * Calculates the lowercase SHA-256 digest of UTF-8 text.
     *
     * @param value text to digest
     * @return lowercase SHA-256 digest
     */
    public static String sha256(String value) {
        return sha256(ProcessText.encodeUtf8(Objects.requireNonNull(value, "value"), "Digest text"));
    }

    /**
     * Calculates the lowercase SHA-256 digest of bytes.
     *
     * @param value bytes to digest
     * @return lowercase SHA-256 digest
     */
    public static String sha256(byte[] value) {
        try {
            return HexFormat
                .of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(Objects.requireNonNull(value, "value")));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by Java", impossible);
        }
    }

    /**
     * Calculates a domain-separated digest of ordered, non-null fields.
     *
     * @param domain stable identity domain
     * @param fields ordered identity fields
     * @return lowercase SHA-256 digest
     */
    public static String sha256Fields(String domain, String... fields) {
        String requiredDomain = Objects.requireNonNull(domain, "domain");
        String[] requiredFields = Objects.requireNonNull(fields, "fields");
        StringBuilder canonical = new StringBuilder(FIELD_FORMAT);
        appendField(canonical, requiredDomain);
        appendField(canonical, Integer.toString(requiredFields.length));
        for (String field : requiredFields) {
            appendField(canonical, Objects.requireNonNull(field, "field"));
        }
        return sha256(canonical.toString());
    }

    private static void appendField(StringBuilder target, String value) {
        target.append(ProcessText.encodeUtf8(value, "Digest text").length).append(':').append(value).append(';');
    }
}
