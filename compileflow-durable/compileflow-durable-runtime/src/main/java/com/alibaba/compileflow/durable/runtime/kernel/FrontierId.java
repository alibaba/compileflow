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
package com.alibaba.compileflow.durable.runtime.kernel;

import com.alibaba.compileflow.durable.api.validation.DurableIdentifiers;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Stable Process-semantic identity of one logical continuation frontier.
 *
 * @author yusu
 */
public record FrontierId(String value) implements Comparable<FrontierId> {
    public static final FrontierId ROOT = new FrontierId("root");

    public FrontierId {
        value = requireText(value, "value");
    }

    /**
     * Derives one static structured branch identity without depending on traversal or scheduling order.
     */
    public static FrontierId branch(FrontierId parent, String splitId, String branchId) {
        FrontierId owner = Objects.requireNonNull(parent, "parent");
        return new FrontierId("f:" + digest("branch", owner.value(), splitId, branchId));
    }

    private static String digest(String domain, String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, domain);
            for (String part : parts) {
                update(digest, requireText(part, "identity part"));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", impossible);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static String requireText(String value, String name) {
        return DurableIdentifiers.requireIdentity(value, name, 128);
    }

    @Override
    public int compareTo(FrontierId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }
}
