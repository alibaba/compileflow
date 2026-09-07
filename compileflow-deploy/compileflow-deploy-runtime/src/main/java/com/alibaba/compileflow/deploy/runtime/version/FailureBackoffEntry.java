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
package com.alibaba.compileflow.deploy.runtime.version;

import java.util.concurrent.TimeUnit;

/**
 * Tracks temporary install backoff after a runtime installation failure.
 *
 * @author yusu
 */
final class FailureBackoffEntry {
    final String reason;
    final long blockedUntilEpochMillis;
    private final long blockedUntilNanos;

    FailureBackoffEntry(String reason, long blockedUntilEpochMillis, long blockedUntilNanos) {
        this.reason = reason;
        this.blockedUntilEpochMillis = blockedUntilEpochMillis;
        this.blockedUntilNanos = blockedUntilNanos;
    }

    boolean isBlocked(long nowNanos) {
        return nowNanos - blockedUntilNanos < 0;
    }

    long remainingMillis(long nowNanos) {
        long remainingNanos = blockedUntilNanos - nowNanos;
        if (remainingNanos <= 0) {
            return 0;
        }
        return Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remainingNanos));
    }
}
