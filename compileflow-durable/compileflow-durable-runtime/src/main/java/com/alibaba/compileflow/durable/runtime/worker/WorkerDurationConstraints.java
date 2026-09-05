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
package com.alibaba.compileflow.durable.runtime.worker;

import java.time.Duration;
import java.util.Objects;

/**
 * Shared representation constraints for Durable Worker scheduling durations.
 *
 * @author yusu
 */
final class WorkerDurationConstraints {
    private WorkerDurationConstraints() {
    }

    static Duration requirePositive(Duration value, String name, Duration maximum) {
        Duration duration = Objects.requireNonNull(value, name);
        if (duration.compareTo(Duration.ofMillis(1)) < 0 || duration.compareTo(maximum) > 0
                || duration.toNanosPart() % 1_000_000 != 0) {
            throw new IllegalArgumentException(
                    name + " must be a positive whole-millisecond duration at most " + maximum);
        }
        return duration;
    }
}
