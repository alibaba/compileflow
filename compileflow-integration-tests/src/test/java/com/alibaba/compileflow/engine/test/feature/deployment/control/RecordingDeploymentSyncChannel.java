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
package com.alibaba.compileflow.engine.test.feature.deployment.control;

import com.alibaba.compileflow.deploy.api.sync.DeploymentSyncChannel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class RecordingDeploymentSyncChannel implements DeploymentSyncChannel {
    private final DeploymentSyncChannel delegate;
    private final List<WriteEvent> writes = Collections.synchronizedList(new ArrayList<>());

    public RecordingDeploymentSyncChannel(DeploymentSyncChannel delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public String read(String key, Duration timeout) throws Exception {
        return delegate.read(key, timeout);
    }

    @Override
    public boolean compareAndSet(String key, String expectedContent, String content, String contentType,
            Duration timeout) throws Exception {
        boolean updated = delegate.compareAndSet(key, expectedContent, content, contentType, timeout);
        if (updated) {
            writes.add(new WriteEvent(System.nanoTime(), key, contentType, content));
        }
        return updated;
    }

    @Override
    public Subscription subscribe(String key, UpdateCallback callback, Duration timeout) throws Exception {
        return delegate.subscribe(key, callback, timeout);
    }

    public List<WriteEvent> getWrites() {
        synchronized (writes) {
            return new ArrayList<>(writes);
        }
    }

    public int indexOfFirstWriteKeyPrefix(String prefix) {
        List<WriteEvent> snapshot = getWrites();
        for (int i = 0; i < snapshot.size(); i++) {
            if (snapshot.get(i).key != null && snapshot.get(i).key.startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }

    public int indexOfFirstWriteKey(String key) {
        List<WriteEvent> snapshot = getWrites();
        for (int i = 0; i < snapshot.size(); i++) {
            if (Objects.equals(snapshot.get(i).key, key)) {
                return i;
            }
        }
        return -1;
    }

    public static final class WriteEvent {
        public final long nanoTime;
        public final String key;
        public final String contentType;
        public final String content;

        WriteEvent(long nanoTime, String key, String contentType, String content) {
            this.nanoTime = nanoTime;
            this.key = key;
            this.contentType = contentType;
            this.content = content;
        }
    }
}
