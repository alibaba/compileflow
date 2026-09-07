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
package com.alibaba.compileflow.deploy.spi.projection;

import java.time.Duration;
import java.util.concurrent.Executor;

/**
 * Provider Preview projection transport used by distributed deployment processes.
 *
 * <p>This version-coupled SPI is not part of the supported deployment application API.
 *
 * <p>Implementations must provide linearizable compare-and-set semantics for each key. Routing
 * ordering and artifact immutability depend on that guarantee; an implementation backed by a
 * remote store must use the store's atomic primitive rather than a client-side read/write pair.
 * Implementations must be thread-safe, honor each supplied operation deadline, and preserve
 * interruption. The application or dependency-injection container owns the store lifecycle;
 * callers own and close returned subscriptions.
 *
 * @author yusu
 */
public interface DeploymentProjectionStore {
    /**
     * Reads the current exact content for one key.
     *
     * @param key     storage key
     * @param timeout positive deadline for the complete operation
     * @return current content, or {@code null} when the key is absent
     * @throws Exception when the store cannot determine the current value
     */
    String read(String key, Duration timeout) throws Exception;

    /**
     * Atomically replaces one value only when its current content matches the expected content.
     *
     * <p>A {@code null} expected value means that the key must be absent. Implementations must
     * compare exact content and must not emulate this operation with a non-atomic read followed by
     * a write.
     *
     * @param key             storage key
     * @param expectedContent exact current content, or {@code null} when the key must be absent
     * @param content         replacement content
     * @param contentType     transport content type
     * @param timeout         positive deadline for the complete operation
     * @return {@code true} when the replacement succeeded; {@code false} on a content mismatch
     * @throws Exception when the store cannot determine or persist the result
     */
    boolean compareAndSet(String key, String expectedContent, String content, String contentType, Duration timeout)
            throws Exception;

    /**
     * Subscribes to successful updates for one key.
     *
     * <p>After this method returns, the implementation must eventually deliver a callback that
     * reflects every subsequent successful update, although intermediate values may be coalesced.
     * Notifications may be duplicated or delayed. A subscriber must read or validate complete
     * versioned state rather than treating callback order as authoritative. Callback failures must
     * be isolated from the transport's listener thread and from other subscriptions.
     *
     * @param key      storage key
     * @param callback update callback
     * @param timeout  positive deadline for establishing the subscription
     * @return closeable subscription owned by the caller
     * @throws Exception when the subscription cannot be established
     */
    Subscription subscribe(String key, UpdateCallback callback, Duration timeout) throws Exception;

    /**
     * A projection-store subscription whose close operation is idempotent.
     */
    interface Subscription extends AutoCloseable {
        /**
         * Stops future update delivery and releases transport resources.
         */
        @Override
        void close();
    }

    /**
     * Receives projection-store update hints.
     */
    interface UpdateCallback {
        /**
         * Handles one updated value.
         *
         * @param key     updated storage key
         * @param content new complete content
         */
        void onUpdate(String key, String content);

        /**
         * Selects the callback executor.
         *
         * @return executor for asynchronous dispatch, or {@code null} for transport-thread dispatch
         */
        default Executor executor() {
            return null;
        }
    }
}
