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
package com.alibaba.compileflow.deploy.spring.boot.autoconfigure.routing;

import com.alibaba.compileflow.deploy.api.protocol.routing.RoutingStateKeys;
import com.alibaba.compileflow.deploy.api.protocol.routing.RoutingStateParser;
import com.alibaba.compileflow.deploy.api.protocol.routing.RoutingStateUpdate;
import com.alibaba.compileflow.deploy.control.routing.RoutingStateDeliveryTarget;
import com.alibaba.compileflow.deploy.runtime.LocalRoutingReconciler;
import com.alibaba.compileflow.deploy.runtime.state.DesiredRoutingState;
import java.time.Duration;
import java.util.Objects;

/**
 * Delivers committed embedded routes through runtime loading before publication.
 *
 * @author yusu
 */
public final class LocalReadyRoutingStateDeliveryTarget implements RoutingStateDeliveryTarget {
    private final LocalRoutingReconciler localRoutingReconciler;
    private final Duration convergenceTimeout;

    public LocalReadyRoutingStateDeliveryTarget(LocalRoutingReconciler localRoutingReconciler,
            Duration convergenceTimeout) {
        this.localRoutingReconciler = Objects.requireNonNull(localRoutingReconciler, "localRoutingReconciler");
        this.convergenceTimeout = Objects.requireNonNull(convergenceTimeout, "convergenceTimeout");
        if (convergenceTimeout.isNegative() || convergenceTimeout.isZero()) {
            throw new IllegalArgumentException("convergenceTimeout must be positive");
        }
    }

    @Override
    public void deliver(String routingKey, String payload) {
        String key = Objects.requireNonNull(routingKey, "routingKey");
        RoutingStateUpdate update = RoutingStateParser.parse(Objects.requireNonNull(payload, "payload"));
        if (update == null) {
            throw new IllegalArgumentException("Invalid routing-state payload for key " + key);
        }
        if (!RoutingStateKeys.matchesAliasState(key, update.getNamespace(), update.getCode(), update.getAlias())) {
            throw new IllegalArgumentException("Routing key and payload identity do not match: key=" + key);
        }
        localRoutingReconciler.applyAndAwait(DesiredRoutingState.from(update), convergenceTimeout);
    }
}
