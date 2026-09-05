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
import com.alibaba.compileflow.engine.core.lifecycle.OperationGate;
import com.alibaba.compileflow.deploy.api.protocol.routing.RoutingStateKeys;
import com.alibaba.compileflow.deploy.api.protocol.routing.RoutingStateParser;
import com.alibaba.compileflow.deploy.api.protocol.routing.RoutingStateUpdate;
import com.alibaba.compileflow.deploy.api.sync.DeploymentSyncChannel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Routing-state subscriber backed by a deployment synchronization channel.
 *
 * @author yusu
 */
public final class DeploymentSyncRoutingStateSubscriber implements RoutingStateSubscriber {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeploymentSyncRoutingStateSubscriber.class);
    private final DeploymentSyncChannel channel;
    private final List<String> stateKeys;
    private final Duration operationTimeout;
    private final DeploymentSyncSubscriptionManager subscriptions;
    private final Map<String, Long> lastAcceptedRevision = new ConcurrentHashMap<>();
    private final Map<String, Object> keyLocks = new ConcurrentHashMap<>();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();
    private volatile OperationGate callbackGate = new OperationGate("RoutingStateSubscriber callbacks");
    private volatile Consumer<DesiredRoutingState> onDesiredState = ignored -> {};
    private boolean closed;

    public DeploymentSyncRoutingStateSubscriber(DeploymentSyncChannel channel, List<String> stateKeys,
            Duration operationTimeout) {
        this.channel = Objects.requireNonNull(channel, "channel");
        List<String> configuredKeys = Objects.requireNonNull(stateKeys, "stateKeys");
        if (configuredKeys.isEmpty()) {
            throw new IllegalArgumentException("stateKeys must contain at least one routing state key");
        }
        LinkedHashSet<String> validatedKeys = new LinkedHashSet<>();
        for (String key : configuredKeys) {
            String validatedKey = Objects.requireNonNull(key, "state key");
            if (validatedKey.isBlank()
                    || !validatedKey.equals(ProcessText.strip(ProcessText.requireUnicode(validatedKey, "state key")))) {
                throw new IllegalArgumentException("state keys must be non-blank and trimmed");
            }
            validatedKeys.add(validatedKey);
        }
        this.stateKeys = Collections.unmodifiableList(new ArrayList<>(validatedKeys));
        this.operationTimeout = Objects.requireNonNull(operationTimeout, "operationTimeout");
        if (operationTimeout.isZero() || operationTimeout.isNegative()) {
            throw new IllegalArgumentException("operationTimeout must be positive");
        }
        this.subscriptions = new DeploymentSyncSubscriptionManager(channel, operationTimeout);
    }

    @Override
    public void start(Consumer<DesiredRoutingState> onDesiredState) {
        Consumer<DesiredRoutingState> callback = Objects.requireNonNull(onDesiredState, "onDesiredState");
        rejectCallbackLifecycle(callbackGate, "started");
        Lock startLock = lifecycleLock.writeLock();
        startLock.lock();
        try {
            if (closed) {
                throw new IllegalStateException("RoutingStateSubscriber is closed");
            }
            if (!started.compareAndSet(false, true)) {
                return;
            }
            callbackGate = new OperationGate("RoutingStateSubscriber callbacks");
            this.onDesiredState = callback;

            try {
                StartupUpdateBuffer startupUpdates = new StartupUpdateBuffer();
                List<ValidatedUpdate> initialUpdates = new ArrayList<>();
                for (String key : stateKeys) {
                    if (!started.get()) {
                        throw new IllegalStateException("RoutingStateSubscriber stopped during startup");
                    }
                    subscriptions.ensureSubscribed(key, (updatedKey, content) -> {
                        if (!key.equals(updatedKey)) {
                            LOGGER.warn("Ignoring routing state callback for an unexpected key: "
                                    + "subscribedKey={} updatedKey={}", key, updatedKey);
                        } else if (started.get()) {
                            startupUpdates.accept(updatedKey, content);
                        }
                    });
                    String initial = channel.read(key, operationTimeout);
                    ValidatedUpdate initialUpdate = validate(key, initial, UpdatePhase.INITIAL_SNAPSHOT);
                    if (initialUpdate != null) {
                        initialUpdates.add(initialUpdate);
                    }
                }
                List<BufferedUpdate> bufferedUpdates = startupUpdates.activate();
                for (ValidatedUpdate initialUpdate : initialUpdates) {
                    emit(initialUpdate, UpdatePhase.INITIAL_SNAPSHOT);
                }
                for (BufferedUpdate bufferedUpdate : bufferedUpdates) {
                    emitIfPresent(bufferedUpdate.key(), bufferedUpdate.content(), UpdatePhase.INITIAL_SNAPSHOT);
                }
            } catch (Exception failure) {
                started.set(false);
                closeCallbackGate(callbackGate, operationTimeout);
                subscriptions.closeAll();
                lastAcceptedRevision.clear();
                keyLocks.clear();
                throw new IllegalStateException("Failed to initialize routing state subscriptions", failure);
            }
            LOGGER.info("RoutingStateSubscriber started: keys={}", stateKeys.size());
        } finally {
            startLock.unlock();
        }
    }

    @Override
    public void stop() {
        rejectCallbackLifecycle(callbackGate, "stopped");
        Lock stopLock = lifecycleLock.writeLock();
        stopLock.lock();
        try {
            OperationGate callbacks = callbackGate;
            if (!started.compareAndSet(true, false)) {
                return;
            }
            closeCallbackGate(callbacks, operationTimeout);
            subscriptions.closeAll();
            lastAcceptedRevision.clear();
            keyLocks.clear();
            LOGGER.info("RoutingStateSubscriber stopped.");
        } finally {
            stopLock.unlock();
        }
    }

    @Override
    public void close() {
        rejectCallbackLifecycle(callbackGate, "closed");
        Lock closeLock = lifecycleLock.writeLock();
        closeLock.lock();
        try {
            OperationGate callbacks = callbackGate;
            if (closed) {
                return;
            }
            closed = true;
            started.set(false);
            closeCallbackGate(callbacks, operationTimeout);
            subscriptions.close();
            lastAcceptedRevision.clear();
            keyLocks.clear();
            LOGGER.info("RoutingStateSubscriber closed.");
        } finally {
            closeLock.unlock();
        }
    }

    private void emitIfPresent(String key, String json) {
        emitIfPresent(key, json, UpdatePhase.LIVE_UPDATE);
    }

    private void emitIfPresent(String key, String json, UpdatePhase phase) {
        ValidatedUpdate update = validate(key, json, phase);
        if (update != null) {
            emit(update, phase);
        }
    }

    private ValidatedUpdate validate(String key, String json, UpdatePhase phase) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        String stateKey = key;
        if (stateKey == null || stateKey.isBlank()) {
            LOGGER.warn("Ignoring routing state payload with a blank key");
            return null;
        }
        RoutingStateUpdate routingStateUpdate;
        try {
            routingStateUpdate = RoutingStateParser.parse(json);
        } catch (Exception failure) {
            if (phase.failFast()) {
                throw new IllegalStateException("Initial routing state payload is invalid: key=" + stateKey, failure);
            }
            LOGGER.warn("Ignoring invalid routing state update and retaining the last valid route: key={}", stateKey,
                    failure);
            return null;
        }
        if (routingStateUpdate == null) {
            return null;
        }
        if (!RoutingStateKeys.matchesAliasState(stateKey, routingStateUpdate.getNamespace(),
                routingStateUpdate.getCode(), routingStateUpdate.getAlias())) {
            IllegalArgumentException mismatch =
                    new IllegalArgumentException("Routing key and payload identity do not match");
            if (phase.failFast()) {
                throw new IllegalStateException("Initial routing state payload identity is invalid: key=" + stateKey,
                        mismatch);
            }
            LOGGER.warn("Ignoring routing state update whose payload identity does not match its key: key={}", stateKey);
            return null;
        }
        return new ValidatedUpdate(stateKey, routingStateUpdate);
    }

    private void emit(ValidatedUpdate update, UpdatePhase phase) {
        OperationGate callbacks = callbackGate;
        try {
            callbacks.enter();
        } catch (IllegalStateException closing) {
            return;
        }
        try {
            if (!started.get()) {
                return;
            }
            String stateKey = update.key();
            RoutingStateUpdate routingStateUpdate = update.state();
            Object keyLock = keyLocks.computeIfAbsent(stateKey, ignored -> new Object());
            synchronized (keyLock) {
                try {
                    if (!started.get()) {
                        return;
                    }
                    if (shouldIgnoreAsStale(stateKey, routingStateUpdate.getAliasRevision())) {
                        return;
                    }

                    onDesiredState.accept(DesiredRoutingState.from(routingStateUpdate));
                    lastAcceptedRevision.put(stateKey, routingStateUpdate.getAliasRevision());
                } catch (Exception failure) {
                    if (phase.failFast()) {
                        throw new IllegalStateException("Failed to apply routing state during startup: key=" + stateKey
                                + " revision=" + routingStateUpdate.getAliasRevision(), failure);
                    }
                    LOGGER.warn("Failed to apply routing state update: key={} revision={}", stateKey,
                            routingStateUpdate.getAliasRevision(), failure);
                }
            }
        } finally {
            callbacks.exit();
        }
    }

    private static void closeCallbackGate(OperationGate callbacks, Duration timeout) {
        OperationGate.DrainResult drain = callbacks.beginCloseAndAwaitDrained(timeout);
        if (!drain.cleanupOwner()) {
            return;
        }
        try {
            if (drain != OperationGate.DrainResult.DRAINED) {
                LOGGER.warn("Routing state callbacks did not drain before cleanup: result={}, active={}", drain,
                        callbacks.activeOperations());
            }
        } finally {
            callbacks.finishClose();
        }
    }

    private static void rejectCallbackLifecycle(OperationGate callbacks, String operation) {
        if (callbacks.isEnteredByCurrentThread()) {
            throw new IllegalStateException("RoutingStateSubscriber cannot be " + operation + " from its callback");
        }
    }

    private boolean shouldIgnoreAsStale(String key, long revision) {
        if (StringUtils.isBlank(key)) {
            return false;
        }
        if (revision <= 0) {
            LOGGER.warn("Ignoring routing state message with invalid revision: key={} revision={}", key, revision);
            return true;
        }
        return revision <= lastAcceptedRevision.getOrDefault(key, 0L);
    }

    private record ValidatedUpdate(String key, RoutingStateUpdate state) {}

    private record BufferedUpdate(String key, String content) {}

    private enum UpdatePhase {
        INITIAL_SNAPSHOT,
        LIVE_UPDATE;
        private boolean failFast() {
            return this == INITIAL_SNAPSHOT;
        }
    }

    private final class StartupUpdateBuffer {
        private final Map<String, String> updates = new LinkedHashMap<>();
        private boolean buffering = true;

        void accept(String key, String content) {
            synchronized (this) {
                if (buffering) {
                    updates.put(key, content);
                    return;
                }
            }
            if (started.get()) {
                emitIfPresent(key, content);
            }
        }

        List<BufferedUpdate> activate() {
            synchronized (this) {
                buffering = false;
                List<BufferedUpdate> buffered =
                        updates
                    .entrySet()
                    .stream()
                    .map(entry -> new BufferedUpdate(entry.getKey(), entry.getValue()))
                    .toList();
                updates.clear();
                return buffered;
            }
        }
    }
}
