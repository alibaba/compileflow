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

import com.alibaba.compileflow.deploy.protocol.RoutingStateKeys;
import com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties.DeploymentRoutingProperties;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Builds routing-state subscription keys from deployment routing properties.
 *
 * @author yusu
 */
public final class RoutingStateKeysBuilder {
    private RoutingStateKeysBuilder() {
    }

    public static List<String> build(DeploymentRoutingProperties props) {
        Objects.requireNonNull(props, "props");

        if (props.getCodes().isEmpty() || props.getAliases().isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String namespace : props.getNamespaces()) {
            for (String code : props.getCodes()) {
                for (String alias : props.getAliases()) {
                    keys.add(RoutingStateKeys.aliasState(props.getKeyPrefix(), namespace, code, alias));
                }
            }
        }
        return List.copyOf(keys);
    }
}
