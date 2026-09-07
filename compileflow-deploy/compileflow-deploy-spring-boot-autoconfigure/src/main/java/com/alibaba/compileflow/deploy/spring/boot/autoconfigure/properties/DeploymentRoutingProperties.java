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
package com.alibaba.compileflow.deploy.spring.boot.autoconfigure.properties;

import com.alibaba.compileflow.spring.boot.autoconfigure.properties.DurationPropertyConstraints;
import com.alibaba.compileflow.engine.ProcessIdentifiers;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Strict binding adapter for the routing key space and runtime subscriptions.
 *
 * @author yusu
 */
public final class DeploymentRoutingProperties {
    private static final int MAX_SUBSCRIPTIONS = 10_000;
    /**
     * Key prefix for alias-route state.
     */
    @NotBlank
    private final String keyPrefix;
    /**
     * Namespaces used to derive routing subscription keys.
     */
    @Size(min = 1)
    private final List<String> namespaces;
    /**
     * Process codes used to derive routing subscription keys.
     */
    private final List<String> codes;
    /**
     * Aliases used to derive alias-weight subscription keys.
     */
    private final List<String> aliases;
    /**
     * Deadline for one routing synchronization-projection store operation.
     */
    @NotNull
    private final Duration operationTimeout;

    public DeploymentRoutingProperties(@DefaultValue("compileflow.deployment.") String keyPrefix,
            @DefaultValue("default") List<String> namespaces, List<String> codes,
            @DefaultValue("production") List<String> aliases, @DefaultValue("5s") Duration operationTimeout) {
        this.keyPrefix = keyPrefix;
        this.namespaces = immutableCopy(namespaces);
        this.codes = immutableCopy(codes);
        this.aliases = immutableCopy(aliases);
        this.operationTimeout = operationTimeout;
    }

    private static boolean containsUniqueExactText(List<String> values) {
        if (values == null) {
            return false;
        }
        HashSet<String> unique = new HashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank() || !value.equals(value.trim()) || !unique.add(value)) {
                return false;
            }
        }
        return true;
    }

    private static boolean allValid(List<String> values, Consumer<String> validator) {
        if (values == null) {
            return false;
        }
        try {
            values.forEach(validator);
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static List<String> immutableCopy(List<String> values) {
        List<String> result = new ArrayList<>();
        if (values != null) {
            result.addAll(values);
        }
        return Collections.unmodifiableList(result);
    }

    @AssertTrue(message = "compileflow.deploy.routing lists must contain exact, non-blank, unique entries")
    public boolean isListContentValid() {
        return containsUniqueExactText(namespaces) && containsUniqueExactText(codes) && containsUniqueExactText(aliases);
    }

    @AssertTrue(message = "compileflow.deploy.routing namespaces, codes, and aliases must use canonical ProcessRef "
            + "identifier syntax")
    public boolean isDerivedIdentifierContentValid() {
        return allValid(namespaces, ProcessIdentifiers::requireNamespace)
                && allValid(codes, ProcessIdentifiers::requireCode)
                && allValid(aliases, ProcessIdentifiers::requireAlias);
    }

    @AssertTrue(message = "compileflow.deploy.routing must produce no more than 10000 subscriptions per runtime node")
    public boolean isSubscriptionCardinalityValid() {
        if (namespaces == null || codes == null || aliases == null) {
            return false;
        }
        long derivedCount = (long) namespaces.size() * codes.size() * aliases.size();
        return derivedCount <= MAX_SUBSCRIPTIONS;
    }

    @AssertTrue(message = "compileflow.deploy.routing.key-prefix must contain at most 128 ASCII letters, digits, "
            + "'.', '_', ':', or '-' without surrounding whitespace")
    public boolean isKeyPrefixValid() {
        return DeploymentProtocolPropertyConstraints.isPortableKeyPrefix(keyPrefix);
    }

    @AssertTrue(message = "compileflow.deploy.routing.operation-timeout must be a positive whole-millisecond "
            + "duration representable as a long")
    public boolean isOperationTimeoutValid() {
        return DurationPropertyConstraints.isPositiveWholeMilliseconds(operationTimeout);
    }

    /**
     * Reports whether at least one explicit or derived routing key can be watched.
     *
     * @return {@code true} when the runtime has a non-empty subscription set
     */
    public boolean hasSubscriptions() {
        return !codes.isEmpty() && !aliases.isEmpty();
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public List<String> getNamespaces() {
        return namespaces;
    }

    public List<String> getCodes() {
        return codes;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public Duration getOperationTimeout() {
        return operationTimeout;
    }
}
