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
package com.alibaba.compileflow.deploy.runtime.state;

import com.alibaba.compileflow.engine.ProcessText;
import com.alibaba.compileflow.deploy.api.sync.DeploymentSyncChannel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns a deduplicated set of deployment-channel subscriptions.
 *
 * <p>The manager is thread-safe. Each key has at most one subscription owned by this instance.
 * {@link #closeAll()} releases current subscriptions while keeping the manager reusable;
 * {@link #close()} permanently terminates it.
 *
 * @author yusu
 */
final class DeploymentSyncSubscriptionManager implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeploymentSyncSubscriptionManager.class);
    private final DeploymentSyncChannel channel;
    private final Duration operationTimeout;
    private final Object lifecycleMonitor = new Object();
    private final Map<String, DeploymentSyncChannel.Subscription> subscriptions = new HashMap<>();
    private boolean closed;

    /**
     * Creates a subscription owner for one channel.
     *
     * @param channel          channel on which subscriptions are established
     * @param operationTimeout deadline for establishing one subscription
     */
    DeploymentSyncSubscriptionManager(DeploymentSyncChannel channel, Duration operationTimeout) {
        this.channel = Objects.requireNonNull(channel, "channel");
        this.operationTimeout = Objects.requireNonNull(operationTimeout, "operationTimeout");
        if (operationTimeout.isZero() || operationTimeout.isNegative()) {
            throw new IllegalArgumentException("operationTimeout must be positive");
        }
    }

    private static void closeSubscriptions(List<DeploymentSyncChannel.Subscription> subscriptions) {
        for (DeploymentSyncChannel.Subscription subscription : subscriptions) {
            try {
                subscription.close();
            } catch (Exception failure) {
                LOGGER.warn("Failed to close subscription; possible listener leak: failureType={}",
                        failure.getClass().getName());
            }
        }
    }

    /**
     * Establishes a subscription unless this manager already owns one for the key.
     *
     * @param key      non-blank, trimmed storage key
     * @param callback update callback
     * @return {@code true} when a new subscription was created; {@code false} when already present
     * @throws IllegalStateException if this manager is closed or channel subscription fails
     */
    boolean ensureSubscribed(String key, DeploymentSyncChannel.UpdateCallback callback) {
        String subscriptionKey = Objects.requireNonNull(key, "key");
        if (subscriptionKey.isBlank()
                || !subscriptionKey.equals(ProcessText.strip(ProcessText.requireUnicode(subscriptionKey, "key")))) {
            throw new IllegalArgumentException("key must be non-blank and trimmed");
        }
        Objects.requireNonNull(callback, "callback");
        synchronized (lifecycleMonitor) {
            if (closed) {
                throw new IllegalStateException("Subscription manager is closed");
            }
            if (subscriptions.containsKey(subscriptionKey)) {
                return false;
            }
            try {
                DeploymentSyncChannel.Subscription subscription = Objects.requireNonNull(channel.subscribe(subscriptionKey,
                                callback, operationTimeout), "channel returned a null subscription");
                subscriptions.put(subscriptionKey, subscription);
                return true;
            } catch (Exception failure) {
                throw new IllegalStateException("subscribe failed: key=" + subscriptionKey, failure);
            }
        }
    }

    /**
     * Closes and forgets all current subscriptions while leaving this manager reusable.
     */
    void closeAll() {
        closeSubscriptions(drainSubscriptions(false));
    }

    /**
     * Permanently closes this manager and every subscription it owns.
     */
    @Override
    public void close() {
        closeSubscriptions(drainSubscriptions(true));
    }

    private List<DeploymentSyncChannel.Subscription> drainSubscriptions(boolean terminate) {
        synchronized (lifecycleMonitor) {
            if (terminate && closed) {
                return List.of();
            }
            if (terminate) {
                closed = true;
            }
            List<DeploymentSyncChannel.Subscription> owned = new ArrayList<>(subscriptions.values());
            subscriptions.clear();
            return owned;
        }
    }
}
