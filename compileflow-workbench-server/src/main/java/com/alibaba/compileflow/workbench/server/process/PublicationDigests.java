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
package com.alibaba.compileflow.workbench.server.process;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Stable domain-separated identities persisted by Workbench publication.
 */
final class PublicationDigests {
    private static final String VERSION_DOMAIN = "compileflow-workbench-publication-version-v1";
    private static final String REQUEST_DOMAIN = "compileflow-workbench-publication-request-v1";

    private PublicationDigests() {
    }

    static String version(String namespace, String code, String idempotencyKey) {
        return "r-" + digest(VERSION_DOMAIN, namespace, code, idempotencyKey).substring(0, 32);
    }

    static String request(String code, long expectedRevision, String changelog) {
        return digest(REQUEST_DOMAIN, code, Long.toString(expectedRevision), changelog);
    }

    private static String digest(String domain, String... fields) {
        MessageDigest digest = sha256();
        update(digest, domain);
        for (String field : fields) {
            update(digest, Objects.requireNonNull(field, "digest field"));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }
}
