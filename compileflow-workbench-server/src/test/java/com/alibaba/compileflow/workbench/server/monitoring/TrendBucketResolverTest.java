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

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class TrendBucketResolverTest {
    @Test
    void oneHourWithOneMinuteIntervalProducesSixtyBuckets() {
        TrendBucketResolver.BucketLayout buckets = TrendBucketResolver.resolve(1, "1m");
        assertThat(buckets.bucketCount()).isEqualTo(60);
        assertThat(buckets.bucketMs()).isEqualTo(60_000L);
    }

    @Test
    void thirtyDaysWithDailyIntervalProducesThirtyBuckets() {
        TrendBucketResolver.BucketLayout buckets = TrendBucketResolver.resolve(720, "1d");
        assertThat(buckets.bucketCount()).isEqualTo(30);
        assertThat(buckets.bucketMs()).isEqualTo(86_400_000L);
    }

    @Test
    void defaultsToHourlyBucketsForDayRange() {
        TrendBucketResolver.BucketLayout buckets = TrendBucketResolver.resolve(24, null);
        assertThat(buckets.bucketCount()).isEqualTo(24);
        assertThat(buckets.bucketMs()).isEqualTo(3_600_000L);
    }
}
