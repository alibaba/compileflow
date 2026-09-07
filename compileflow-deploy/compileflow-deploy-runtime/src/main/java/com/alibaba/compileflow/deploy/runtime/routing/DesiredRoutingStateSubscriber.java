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
package com.alibaba.compileflow.deploy.runtime.routing;

import java.util.function.Consumer;

/**
 * Subscribes to desired routing-state updates for a deployment runtime.
 *
 * @author yusu
 */
public interface DesiredRoutingStateSubscriber extends AutoCloseable {
    /**
     * Starts delivery of validated desired states.
     *
     * <p>Returning from the callback acknowledges synchronous admission of
     * the desired revision. Installation, retry, and local-ready publication
     * remain the downstream convergence owner's responsibility.
     *
     * @param onDesiredState synchronous desired-state admission callback
     */
    void start(Consumer<DesiredRoutingState> onDesiredState);

    void stop();

    @Override
    void close();
}
