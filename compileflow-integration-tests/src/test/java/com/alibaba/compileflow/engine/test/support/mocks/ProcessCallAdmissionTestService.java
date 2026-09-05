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

import java.util.concurrent.atomic.AtomicInteger;

public final class ProcessCallAdmissionTestService {
    private static final AtomicInteger INVOCATIONS = new AtomicInteger();

    public void mark() {
        INVOCATIONS.incrementAndGet();
    }

    public static int invocations() {
        return INVOCATIONS.get();
    }

    public static void reset() {
        INVOCATIONS.set(0);
    }
}
