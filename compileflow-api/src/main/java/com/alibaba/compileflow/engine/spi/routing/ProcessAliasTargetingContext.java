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
package com.alibaba.compileflow.engine.spi.routing;

import com.alibaba.compileflow.engine.AliasRoutingOptions;
import com.alibaba.compileflow.engine.ProcessRef;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable inputs exposed to one Alias targeting policy evaluation.
 *
 * <p>The context deliberately omits candidate weight so policies cannot redefine percentage
 * semantics. String representations expose only field names, never routing or parameter values.
 *
 * @author yusu
 */
public final class ProcessAliasTargetingContext {
    private final ProcessRef.Alias alias;
    private final ProcessRef.Version stableVersion;
    private final ProcessRef.Version candidateVersion;
    private final Map<String, String> parameters;
    private final AliasRoutingOptions options;

    private ProcessAliasTargetingContext(ProcessAliasRoute route, AliasRoutingOptions options) {
        ProcessAliasRoute source = Objects.requireNonNull(route, "route");
        AliasTargeting targeting = Objects.requireNonNull(source.targeting(), "route.targeting");
        this.alias = source.alias();
        this.stableVersion = source.stableVersion();
        this.candidateVersion = Objects.requireNonNull(source.candidateVersion(), "route.candidateVersion");
        this.parameters = targeting.parameters();
        this.options = Objects.requireNonNull(options, "options");
    }

    /**
     * Creates the policy context for a route with targeting configuration.
     *
     * @param route authoritative Alias route
     * @param options request-scoped routing inputs
     * @return immutable targeting context
     */
    public static ProcessAliasTargetingContext of(ProcessAliasRoute route, AliasRoutingOptions options) {
        return new ProcessAliasTargetingContext(route, options);
    }

    public ProcessRef.Alias getAlias() {
        return alias;
    }

    public ProcessRef.Version getStableVersion() {
        return stableVersion;
    }

    public ProcessRef.Version getCandidateVersion() {
        return candidateVersion;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public String getRoutingKey() {
        return options.routingKey();
    }

    public Map<String, String> getAttributes() {
        return options.attributes();
    }

    @Override
    public String toString() {
        return "ProcessAliasTargetingContext{alias=" + alias + ", stableVersion=" + stableVersion
                + ", candidateVersion=" + candidateVersion + ", routingKeyPresent=" + (getRoutingKey() != null)
                + ", parameterNames=" + parameters.keySet() + ", attributeNames=" + getAttributes().keySet() + '}';
    }
}
