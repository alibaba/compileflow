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
package com.alibaba.compileflow.durable.spring.boot.autoconfigure.runtime;

import com.alibaba.compileflow.durable.api.DurableProcessEngine;
import java.util.Objects;
import org.springframework.context.SmartLifecycle;

/**
 * Adapts Spring startup and shutdown timing to the framework-neutral worker owner.
 */
public final class DurableWorkerLifecycle implements SmartLifecycle {
    private final DurableProcessEngine engine;

    public DurableWorkerLifecycle(DurableProcessEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    @Override
    public void start() {
        engine.start();
    }

    @Override
    public void stop() {
        engine.stop();
    }

    @Override
    public void stop(Runnable callback) {
        Objects.requireNonNull(callback, "callback");
        Thread waiter = new Thread(() -> {
            try {
                engine.stop();
            } finally {
                callback.run();
            }
        }, "compileflow-durable-spring-stop");
        waiter.setDaemon(true);
        waiter.start();
    }

    @Override
    public boolean isRunning() {
        return engine.isRunning();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }
}
