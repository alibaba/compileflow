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
package com.alibaba.compileflow.engine.core.runtime.concurrent;

import java.util.Collection;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Helpers for waiting on futures with timeouts.
 *
 * @author yusu
 */
public final class FutureTimeouts {
    private FutureTimeouts() {
    }

    public static <T> T getWithoutCancellation(Future<T> future, long timeoutMs)
            throws TimeoutException, ExecutionException, InterruptedException, CancellationException {
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be positive, got: " + timeoutMs);
        }
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw ie;
        }
    }

    public static void awaitAllWithoutCancellation(Collection<? extends CompletableFuture<?>> futures, long timeoutMs)
            throws TimeoutException, InterruptedException, ExecutionException, CancellationException {
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be positive, got: " + timeoutMs);
        }

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(CompletableFuture<?>[]::new));
        getWithoutCancellation(allFutures, timeoutMs);
    }
}
