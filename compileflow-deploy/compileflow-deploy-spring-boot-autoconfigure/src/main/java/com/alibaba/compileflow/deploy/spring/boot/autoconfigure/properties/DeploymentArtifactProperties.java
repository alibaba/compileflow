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
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Strict binding adapter for deployment artifact storage and integrity policy.
 *
 * @author yusu
 */
public final class DeploymentArtifactProperties {
    /**
     * Authoritative transport used to publish and resolve process artifacts.
     */
    @NotNull
    private final Mode mode;
    /**
     * Key prefix used when artifacts are stored in a deployment projection store.
     */
    @NotBlank
    private final String keyPrefix;
    /**
     * Deadline for one artifact synchronization-projection store operation.
     */
    @NotNull
    private final Duration operationTimeout;

    public DeploymentArtifactProperties(@DefaultValue("SOURCE") Mode mode,
            @DefaultValue("compileflow.process.") String keyPrefix, @DefaultValue("5s") Duration operationTimeout) {
        this.mode = mode;
        this.keyPrefix = keyPrefix;
        this.operationTimeout = operationTimeout;
    }

    @AssertTrue(message = "compileflow.deploy.artifact.key-prefix must contain at most 128 ASCII letters, digits, "
            + "'.', '_', ':', or '-' without surrounding whitespace")
    public boolean isKeyPrefixValid() {
        return DeploymentProtocolPropertyConstraints.isPortableKeyPrefix(keyPrefix);
    }

    @AssertTrue(message = "compileflow.deploy.artifact.operation-timeout must be a positive whole-millisecond "
            + "duration representable as a long")
    public boolean isOperationTimeoutValid() {
        return DurationPropertyConstraints.isPositiveWholeMilliseconds(operationTimeout);
    }

    public Mode getMode() {
        return mode;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public Duration getOperationTimeout() {
        return operationTimeout;
    }

    /**
     * Authoritative transport used to publish and resolve immutable process artifacts.
     */
    public enum Mode {
        /**
         * Resolve artifacts from the version repository.
         */
        SOURCE,
        /**
         * Publish and resolve artifacts through the deployment projection store.
         */
        PROJECTION_STORE
    }
}
