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
package com.alibaba.compileflow.engine.spring.boot.autoconfigure.properties;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Immutable static plugin discovery settings for one process engine configuration.
 *
 * @author yusu
 */
public final class EnginePluginProperties {
    /**
     * Whether ProcessEnginePlugin implementations are discovered via ServiceLoader.
     */
    private final boolean discoveryEnabled;

    /**
     * Creates an immutable plugin binding snapshot.
     *
     * @param discoveryEnabled whether classpath plugin discovery is enabled
     */
    public EnginePluginProperties(@DefaultValue("false") boolean discoveryEnabled) {
        this.discoveryEnabled = discoveryEnabled;
    }

    public boolean isDiscoveryEnabled() {
        return discoveryEnabled;
    }

    @Override
    public String toString() {
        return "EnginePluginProperties{discoveryEnabled=" + discoveryEnabled + '}';
    }
}
