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
package com.alibaba.compileflow.engine.test.support.mocks;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Coordinates generated-code tests that verify parallel commit atomicity.
 *
 * @author yusu
 */
public class AtomicParallelService {
    private static volatile CountDownLatch outputWritten = new CountDownLatch(1);

    public static void reset() {
        outputWritten = new CountDownLatch(1);
    }

    public Integer produceValue() {
        return 42;
    }

    public void confirmOutputWritten(Integer value) {
        if (!Integer.valueOf(42).equals(value)) {
            throw new IllegalStateException("branch-local output was not visible");
        }
        outputWritten.countDown();
    }

    public void failAfterOutputWritten() throws InterruptedException {
        if (!outputWritten.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for branch output confirmation");
        }
        throw new IllegalStateException("planned branch failure");
    }
}
