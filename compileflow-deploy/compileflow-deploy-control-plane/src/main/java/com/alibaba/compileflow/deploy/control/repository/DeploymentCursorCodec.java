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
package com.alibaba.compileflow.deploy.control.repository;

import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.ProcessText;
import com.alibaba.compileflow.deploy.api.rollout.RolloutConstraints;
import com.alibaba.compileflow.deploy.api.rollout.RolloutCursor;
import com.alibaba.compileflow.deploy.api.version.PublishedVersionCursor;
import com.alibaba.compileflow.deploy.spi.store.PublishedVersionPageKey;
import com.alibaba.compileflow.deploy.spi.store.RolloutPageKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * Versioned encoding for public opaque Deploy cursors.
 *
 * @author yusu
 */
public final class DeploymentCursorCodec {
    private static final String VERSION = "cf-deploy-cursor/v1/version";
    private static final String ROLLOUT = "cf-deploy-cursor/v1/rollout";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private DeploymentCursorCodec() {
    }

    /**
     * Encodes the last returned published-version key.
     */
    public static PublishedVersionCursor publishedVersion(long createdAt, String version) {
        return new PublishedVersionCursor(
                encode(VERSION + '|' + positiveTime(createdAt) + '|' + ProcessIdentifiers.requireVersion(version)));
    }

    /**
     * Decodes a published-version cursor, or returns {@code null}.
     */
    public static PublishedVersionPageKey publishedVersion(PublishedVersionCursor cursor) {
        if (cursor == null) {
            return null;
        }
        String[] fields = decode(cursor.value(), VERSION);
        return new PublishedVersionPageKey(parseTime(fields[1]), ProcessIdentifiers.requireVersion(fields[2]));
    }

    /**
     * Encodes the last returned rollout key.
     */
    public static RolloutCursor rollout(long createdAt, String rolloutId) {
        String id = RolloutConstraints.requireId(rolloutId, "rolloutId");
        if (id.indexOf('|') >= 0) {
            throw new IllegalArgumentException("rolloutId must not contain '|'");
        }
        return new RolloutCursor(encode(ROLLOUT + '|' + positiveTime(createdAt) + '|' + id));
    }

    /**
     * Decodes a rollout cursor, or returns {@code null}.
     */
    public static RolloutPageKey rollout(RolloutCursor cursor) {
        if (cursor == null) {
            return null;
        }
        String[] fields = decode(cursor.value(), ROLLOUT);
        return new RolloutPageKey(parseTime(fields[1]), RolloutConstraints.requireId(fields[2], "rolloutId"));
    }

    private static String encode(String value) {
        return ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String[] decode(String value, String type) {
        try {
            byte[] bytes = DECODER.decode(Objects.requireNonNull(value, "value"));
            if (!ENCODER.encodeToString(bytes).equals(value)) {
                throw new IllegalArgumentException("Non-canonical encoding");
            }
            String[] fields = ProcessText.decodeUtf8(bytes, "Cursor").split("\\|", -1);
            if (fields.length != 3 || !type.equals(fields[0])) {
                throw new IllegalArgumentException("Unexpected cursor type or shape");
            }
            return fields;
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("Invalid Deploy cursor", failure);
        }
    }

    private static long parseTime(String value) {
        try {
            return positiveTime(Long.parseLong(value));
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Invalid Deploy cursor timestamp", failure);
        }
    }

    private static long positiveTime(long value) {
        if (value <= 0) {
            throw new IllegalArgumentException("cursor timestamp must be positive");
        }
        return value;
    }
}
