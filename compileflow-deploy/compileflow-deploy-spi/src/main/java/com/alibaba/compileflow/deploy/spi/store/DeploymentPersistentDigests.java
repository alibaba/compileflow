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
package com.alibaba.compileflow.deploy.spi.store;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Canonical domain-separated hashes for facts persisted by Deploy providers.
 *
 * <p>The domains and length-prefixed encoding are part of the version-coupled persistence
 * contract. Providers must not substitute database-specific digest encodings.
 *
 * @author yusu
 */
public final class DeploymentPersistentDigests {
    private static final String ROLLOUT_REQUEST_DOMAIN = "compileflow-deploy-rollout-request-v1";
    private static final String ROUTING_OUTBOX_DOMAIN = "compileflow-deploy-routing-outbox-v1";

    private DeploymentPersistentDigests() {
    }

    /**
     * Hashes the canonical rollout request fields.
     *
     * @param fields ordered non-null request fields
     * @return lowercase SHA-256 digest
     */
    public static String rolloutRequest(String... fields) {
        return digest(ROLLOUT_REQUEST_DOMAIN, fields);
    }

    /**
     * Hashes the canonical routing-outbox delivery fields.
     *
     * @param fields ordered non-null delivery fields
     * @return lowercase SHA-256 digest
     */
    public static String routingOutbox(String... fields) {
        return digest(ROUTING_OUTBOX_DOMAIN, fields);
    }

    private static String digest(String domain, String... fields) {
        MessageDigest digest = sha256();
        update(digest, domain);
        for (String field : Objects.requireNonNull(fields, "fields")) {
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
