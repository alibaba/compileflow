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
package com.alibaba.compileflow.deploy.api.sync.inmemory;

import com.alibaba.compileflow.deploy.api.sync.DeploymentSyncChannel;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe, process-local deployment channel for deterministic tests.
 *
 * <p>State is neither durable nor shared with another JVM. This implementation must not be used
 * as a production transport or as a fallback when a configured transport is unavailable.
 * Compare-and-set is linearizable per key; callbacks are update hints and may be observed out of
 * order.
 *
 * @author yusu
 */
public final class InMemoryDeploymentSyncChannel implements DeploymentSyncChannel {
    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryDeploymentSyncChannel.class);
    private final Map<String, String> store = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<Subscriber>> callbacks = new ConcurrentHashMap<>();

    /**
     * Creates an empty process-local channel with independent state and subscriptions.
     */
    public InMemoryDeploymentSyncChannel() {
    }

    private static String requireText(String value, String name) {
        String text = Objects.requireNonNull(value, name);
        if (text.isBlank() || !text.equals(text.trim())) {
            throw new IllegalArgumentException(name + " must be non-blank and trimmed");
        }
        return text;
    }

    private static void requirePositiveTimeout(Duration timeout) {
        Duration value = Objects.requireNonNull(timeout, "timeout");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    @Override
    public String read(String key, Duration timeout) {
        requirePositiveTimeout(timeout);
        return store.get(requireText(key, "key"));
    }

    @Override
    public boolean compareAndSet(String key, String expectedContent, String content, String contentType,
            Duration timeout) {
        requirePositiveTimeout(timeout);
        String channelKey = requireText(key, "key");
        Objects.requireNonNull(content, "content");
        requireText(contentType, "contentType");
        AtomicBoolean updated = new AtomicBoolean();
        store.compute(channelKey, (ignored, current) -> {
            if (!Objects.equals(current, expectedContent)) {
                return current;
            }
            updated.set(true);
            return content;
        });
        if (updated.get()) {
            notifyCallbacks(channelKey, content);
        }
        return updated.get();
    }

    @Override
    public Subscription subscribe(String key, UpdateCallback callback, Duration timeout) {
        requirePositiveTimeout(timeout);
        String channelKey = requireText(key, "key");
        Subscriber subscriber = new Subscriber(Objects.requireNonNull(callback, "callback"));
        callbacks.compute(channelKey, (ignored, current) -> {
            CopyOnWriteArrayList<Subscriber> subscribers = current == null ? new CopyOnWriteArrayList<>() : current;
            subscribers.add(subscriber);
            return subscribers;
        });
        return () -> {
            if (!subscriber.close()) {
                return;
            }
            callbacks.computeIfPresent(channelKey, (ignored, subscribers) -> {
                subscribers.remove(subscriber);
                return subscribers.isEmpty() ? null : subscribers;
            });
        };
    }

    int subscriptionKeyCount() {
        return callbacks.size();
    }

    private void notifyCallbacks(String key, String content) {
        CopyOnWriteArrayList<Subscriber> list = callbacks.get(key);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (Subscriber subscriber : list) {
            if (!subscriber.isActive()) {
                continue;
            }
            UpdateCallback callback = subscriber.callback;
            try {
                Executor executor = callback.executor();
                if (executor == null) {
                    subscriber.notify(key, content);
                } else {
                    executor.execute(() -> {
                        try {
                            subscriber.notify(key, content);
                        } catch (Exception failure) {
                            LOGGER.warn("Async callback failed for key={}", key, failure);
                        }
                    });
                }
            } catch (Exception failure) {
                LOGGER.warn("Callback failed for key={}", key, failure);
            }
        }
    }

    private static final class Subscriber {
        private final UpdateCallback callback;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private Subscriber(UpdateCallback callback) {
            this.callback = callback;
        }

        private boolean isActive() {
            return active.get();
        }

        private void notify(String key, String content) {
            if (active.get()) {
                callback.onUpdate(key, content);
            }
        }

        private boolean close() {
            return active.compareAndSet(true, false);
        }
    }
}
