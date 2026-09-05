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
package com.alibaba.compileflow.engine.core.routing;

import com.alibaba.compileflow.engine.ProcessAliasTarget;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasRoute;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Canonical SHA-256 selector for a published stable/candidate Alias route.
 *
 * @author yusu
 */
public final class DeterministicAliasSelector {
    private static final byte[] MAGIC = "CFROUTE1".getBytes(StandardCharsets.US_ASCII);
    private static final int BUCKET_COUNT = 10_000;

    private DeterministicAliasSelector() {
    }

    /**
     * Selects an authorized target using the route weight and deterministic cohort key.
     *
     * @param route      authoritative Alias route
     * @param routingKey effective deterministic cohort key
     * @return stable or candidate target
     */
    public static ProcessAliasTarget select(ProcessAliasRoute route, String routingKey) {
        ProcessAliasRoute authoritative = Objects.requireNonNull(route, "route");
        if (authoritative.candidateVersion() == null) {
            return ProcessAliasTarget.STABLE;
        }
        String cohort = Objects.requireNonNull(routingKey, "effective routing key is required for candidate selection");
        return bucket(authoritative, cohort) < authoritative.candidateWeightBps()
                ? ProcessAliasTarget.CANDIDATE
                : ProcessAliasTarget.STABLE;
    }

    static int bucket(ProcessAliasRoute route, String routingKey) {
        byte[] hash = hash(route, routingKey);
        long first64Bits = ByteBuffer.wrap(hash).getLong();
        return (int) Long.remainderUnsigned(first64Bits, BUCKET_COUNT);
    }

    static byte[] hash(ProcessAliasRoute route, String routingKey) {
        ProcessAliasRoute authoritative = Objects.requireNonNull(route, "route");
        if (authoritative.candidateVersion() == null) {
            throw new IllegalArgumentException("candidateVersion is required to compute an alias routing hash");
        }
        String cohort = Objects.requireNonNull(routingKey, "routingKey is required to compute an alias routing hash");
        MessageDigest digest = sha256();
        digest.update(MAGIC);
        updateField(digest, authoritative.alias().namespace());
        updateField(digest, authoritative.alias().code());
        updateField(digest, authoritative.alias().alias());
        updateField(digest, authoritative.candidateVersion().version());
        updateField(digest, cohort);
        return digest.digest();
    }

    private static void updateField(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int length = bytes.length;
        digest.update((byte) (length >>> 24));
        digest.update((byte) (length >>> 16));
        digest.update((byte) (length >>> 8));
        digest.update((byte) length);
        digest.update(bytes);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", failure);
        }
    }
}
