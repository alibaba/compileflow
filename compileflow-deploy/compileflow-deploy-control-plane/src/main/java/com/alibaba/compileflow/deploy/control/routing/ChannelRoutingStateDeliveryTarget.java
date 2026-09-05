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
package com.alibaba.compileflow.deploy.control.routing;

import com.alibaba.compileflow.deploy.api.protocol.routing.RoutingStateKeys;
import com.alibaba.compileflow.deploy.api.protocol.routing.RoutingStateParser;
import com.alibaba.compileflow.deploy.api.protocol.routing.RoutingStateUpdate;
import com.alibaba.compileflow.deploy.api.sync.DeploymentSyncChannel;
import java.time.Duration;
import java.util.Objects;

/**
 * Delivers routing-state projections through a distributed synchronization channel.
 *
 * @author yusu
 */
public final class ChannelRoutingStateDeliveryTarget implements RoutingStateDeliveryTarget {
    private static final int MAX_COMPARE_AND_SET_ATTEMPTS = 2;
    private final DeploymentSyncChannel channel;
    private final Duration operationTimeout;

    /**
     * Creates a channel-backed delivery target.
     *
     * @param channel          required deployment synchronization channel
     * @param operationTimeout positive deadline for each channel operation
     */
    public ChannelRoutingStateDeliveryTarget(DeploymentSyncChannel channel, Duration operationTimeout) {
        this.channel = Objects.requireNonNull(channel, "channel");
        this.operationTimeout = Objects.requireNonNull(operationTimeout, "operationTimeout");
        if (operationTimeout.isZero() || operationTimeout.isNegative()) {
            throw new IllegalArgumentException("operationTimeout must be positive");
        }
    }

    private static RoutingStateUpdate parseForKey(String key, String payload) {
        RoutingStateUpdate state = RoutingStateParser.parse(payload);
        if (state == null) {
            throw new IllegalArgumentException("Routing channel payload must not be blank: key=" + key);
        }
        if (!RoutingStateKeys.matchesAliasState(key, state.getNamespace(), state.getCode(), state.getAlias())) {
            throw new IllegalArgumentException("Routing channel key and payload identity do not match: key=" + key);
        }
        return state;
    }

    private static boolean sameState(RoutingStateUpdate first, RoutingStateUpdate second) {
        return first.getNamespace().equals(second.getNamespace()) && first.getCode().equals(second.getCode())
                && first.getAlias().equals(second.getAlias())
                && Objects.equals(first.getStableVersion(), second.getStableVersion())
                && Objects.equals(first.getCandidateVersion(), second.getCandidateVersion())
                && first.getCandidateWeightBps() == second.getCandidateWeightBps()
                && first.isDeleted() == second.isDeleted() && first.getAliasRevision() == second.getAliasRevision()
                && first.getActor().equals(second.getActor()) && first.getUpdatedAt() == second.getUpdatedAt();
    }

    @Override
    public void deliver(String routingKey, String payload) throws Exception {
        String key = Objects.requireNonNull(routingKey, "routingKey");
        String desiredPayload = Objects.requireNonNull(payload, "payload");
        RoutingStateUpdate desired = parseForKey(key, desiredPayload);

        for (int attempt = 0; attempt < MAX_COMPARE_AND_SET_ATTEMPTS; attempt++) {
            String currentPayload = channel.read(key, operationTimeout);
            if (currentPayload != null) {
                RoutingStateUpdate current = parseForKey(key, currentPayload);
                int revisionOrder = Long.compare(current.getAliasRevision(), desired.getAliasRevision());
                if (revisionOrder > 0) {
                    return;
                }
                if (revisionOrder == 0) {
                    if (sameState(current, desired)) {
                        return;
                    }
                    throw new IllegalStateException(
                            "Routing channel contains conflicting content at revision " + desired.getAliasRevision() + ": key=" + key);
                }
            }

            if (channel.compareAndSet(key, currentPayload, desiredPayload, "application/json", operationTimeout)) {
                return;
            }
        }
        throw new IllegalStateException(
                "Routing channel remained contended after " + MAX_COMPARE_AND_SET_ATTEMPTS + " compare-and-set attempts: key=" + key);
    }
}
