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
package com.alibaba.compileflow.deploy.spring.boot.autoconfigure;

import java.util.Objects;
import org.springframework.context.SmartLifecycle;

/**
 * Adapts explicit start/stop actions to the Spring application lifecycle.
 *
 * @author yusu
 */
final class ActionBackedLifecycle implements SmartLifecycle {
    private final Object monitor = new Object();
    private final Runnable startAction;
    private final Runnable stopAction;
    private final int phase;
    private volatile boolean running;

    ActionBackedLifecycle(Runnable startAction, Runnable stopAction, int phase) {
        this.startAction = Objects.requireNonNull(startAction, "startAction");
        this.stopAction = Objects.requireNonNull(stopAction, "stopAction");
        this.phase = phase;
    }

    @Override
    public void start() {
        synchronized (monitor) {
            if (running) {
                return;
            }
            startAction.run();
            running = true;
        }
    }

    @Override
    public void stop() {
        synchronized (monitor) {
            if (!running) {
                return;
            }
            try {
                stopAction.run();
            } finally {
                running = false;
            }
        }
    }

    @Override
    public void stop(Runnable callback) {
        try {
            stop();
        } finally {
            callback.run();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return phase;
    }
}
