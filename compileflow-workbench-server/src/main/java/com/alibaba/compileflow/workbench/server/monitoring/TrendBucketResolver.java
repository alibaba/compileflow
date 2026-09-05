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
package com.alibaba.compileflow.workbench.server.monitoring;

/**
 * Resolves monitoring trend bucket boundaries.
 *
 * @author yusu
 */
final class TrendBucketResolver {
    private static final long MS_PER_MINUTE = 60_000L;
    private static final long MS_PER_HOUR = 3_600_000L;
    private static final long MS_PER_DAY = 86_400_000L;
    private static final int MAX_BUCKETS = 720;

    private TrendBucketResolver() {
    }

    static BucketLayout resolve(int hours, String interval) {
        long rangeMs = (long) hours * MS_PER_HOUR;
        long bucketMs = resolveBucketMs(interval, hours);
        int bucketCount = (int) Math.max(1, rangeMs / bucketMs);
        if (bucketCount > MAX_BUCKETS) {
            bucketMs = Math.max(MS_PER_MINUTE, rangeMs / MAX_BUCKETS);
            bucketCount = (int) Math.max(1, rangeMs / bucketMs);
        }
        return new BucketLayout(bucketMs, bucketCount, rangeMs);
    }

    private static long resolveBucketMs(String interval, int hours) {
        if (interval != null && !interval.trim().isEmpty()) {
            switch (interval.trim()) {
                case "1m":
                    return MS_PER_MINUTE;
                case "5m":
                    return 5 * MS_PER_MINUTE;
                case "1h":
                    return MS_PER_HOUR;
                case "1d":
                    return MS_PER_DAY;
                default:
                    break;
            }
        }
        return hours > 168 ? MS_PER_DAY : MS_PER_HOUR;
    }

    record BucketLayout(long bucketMs, int bucketCount, long rangeMs) {}
}
