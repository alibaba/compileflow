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
package com.alibaba.compileflow.deploy.api.error;

import com.alibaba.compileflow.engine.ProcessRef;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/**
 * Structured deployment failure with a bounded error code and optional process context.
 *
 * <p>Context fields are normalized to {@code null} when blank. They are intended for transport
 * mapping and structured logs; callers must not expose exception messages or causes as stable
 * machine contracts.
 *
 * @author yusu
 */
public final class DeploymentException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    /**
     * Stable bounded failure category.
     *
     * @serial stable bounded failure category
     */
    private final DeploymentErrorCode errorCode;
    /**
     * Optional process namespace context.
     *
     * @serial optional process namespace context
     */
    private final String namespace;
    /**
     * Optional process code context.
     *
     * @serial optional process code context
     */
    private final String code;
    /**
     * Optional immutable process version context.
     *
     * @serial optional immutable process version context
     */
    private final String version;
    /**
     * Optional published Alias context.
     *
     * @serial optional published Alias context
     */
    private final String alias;

    private DeploymentException(DeploymentErrorCode errorCode, String message, Throwable cause, String namespace,
            String code, String version, String alias) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        this.namespace = StringUtils.trimToNull(namespace);
        this.code = StringUtils.trimToNull(code);
        this.version = StringUtils.trimToNull(version);
        this.alias = StringUtils.trimToNull(alias);
    }

    /**
     * Creates a deployment failure without process context.
     *
     * @param errorCode stable failure category
     * @param message   human-readable diagnostic message
     * @return the constructed exception
     */
    public static DeploymentException of(DeploymentErrorCode errorCode, String message) {
        return new DeploymentException(errorCode, message, null, null, null, null, null);
    }

    /**
     * Creates a deployment failure without process context.
     *
     * @param errorCode stable failure category
     * @param message   human-readable diagnostic message
     * @param cause     underlying failure
     * @return the constructed exception
     */
    public static DeploymentException of(DeploymentErrorCode errorCode, String message, Throwable cause) {
        return new DeploymentException(errorCode, message, cause, null, null, null, null);
    }

    /**
     * Creates a deployment failure for an immutable process version.
     *
     * @param errorCode stable failure category
     * @param message   human-readable diagnostic message
     * @param ref       version identity, or {@code null} when unavailable
     * @return the constructed exception
     */
    public static DeploymentException fromRef(DeploymentErrorCode errorCode, String message, ProcessRef.Version ref) {
        if (ref == null) {
            return of(errorCode, message);
        }
        return new DeploymentException(errorCode, message, null, ref.namespace(), ref.code(), ref.version(), null);
    }

    /**
     * Creates a deployment failure for an immutable process version.
     *
     * @param errorCode stable failure category
     * @param message   human-readable diagnostic message
     * @param ref       version identity, or {@code null} when unavailable
     * @param cause     underlying failure
     * @return the constructed exception
     */
    public static DeploymentException fromRef(DeploymentErrorCode errorCode, String message, ProcessRef.Version ref,
            Throwable cause) {
        if (ref == null) {
            return of(errorCode, message, cause);
        }
        return new DeploymentException(errorCode, message, cause, ref.namespace(), ref.code(), ref.version(), null);
    }

    /**
     * Starts a builder for a failure with optional context fields.
     *
     * @param errorCode stable failure category
     * @param message   human-readable diagnostic message
     * @return a new exception builder
     */
    public static Builder builder(DeploymentErrorCode errorCode, String message) {
        return new Builder(errorCode, message, null);
    }

    /**
     * Starts a builder for a failure with optional context fields.
     *
     * @param errorCode stable failure category
     * @param message   human-readable diagnostic message
     * @param cause     underlying failure
     * @return a new exception builder
     */
    public static Builder builder(DeploymentErrorCode errorCode, String message, Throwable cause) {
        return new Builder(errorCode, message, cause);
    }

    /**
     * Returns the stable failure category.
     *
     * @return non-null error code
     */
    public DeploymentErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * Returns the process namespace context.
     *
     * @return namespace, or {@code null}
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Returns the process code context.
     *
     * @return process code, or {@code null}
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns the immutable process version context.
     *
     * @return process version, or {@code null}
     */
    public String getVersion() {
        return version;
    }

    /**
     * Returns the published Alias context.
     *
     * @return alias, or {@code null}
     */
    public String getAlias() {
        return alias;
    }

    /**
     * Returns bounded non-null context fields for structured logging.
     *
     * @return immutable insertion-ordered field map containing at least {@code errorCode}
     */
    public Map<String, String> toLogFields() {
        Map<String, String> fields = new LinkedHashMap<>(8);
        fields.put("errorCode", String.valueOf(errorCode));
        if (namespace != null) {
            fields.put("namespace", namespace);
        }
        if (code != null) {
            fields.put("code", code);
        }
        if (version != null) {
            fields.put("version", version);
        }
        if (alias != null) {
            fields.put("alias", alias);
        }
        return Collections.unmodifiableMap(fields);
    }

    /**
     * Mutable construction helper for one immutable {@link DeploymentException}.
     *
     * <p>A builder is not thread-safe and should be discarded after {@link #build()}.
     */
    public static final class Builder {
        private final DeploymentErrorCode errorCode;
        private final String message;
        private final Throwable cause;
        private String namespace;
        private String code;
        private String version;
        private String alias;

        private Builder(DeploymentErrorCode errorCode, String message, Throwable cause) {
            this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
            this.message = message;
            this.cause = cause;
        }

        /**
         * Sets the process namespace context.
         *
         * @param namespace process namespace, or {@code null}
         * @return this builder
         */
        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        /**
         * Sets the process code context.
         *
         * @param code process code, or {@code null}
         * @return this builder
         */
        public Builder code(String code) {
            this.code = code;
            return this;
        }

        /**
         * Sets the immutable process version context.
         *
         * @param version process version, or {@code null}
         * @return this builder
         */
        public Builder version(String version) {
            this.version = version;
            return this;
        }

        /**
         * Sets the published Alias context.
         *
         * @param alias published Alias, or {@code null}
         * @return this builder
         */
        public Builder alias(String alias) {
            this.alias = alias;
            return this;
        }

        /**
         * Constructs the exception. {@code fillInStackTrace()} is invoked exactly once here.
         *
         * @return deployment exception with the configured context fields
         */
        public DeploymentException build() {
            return new DeploymentException(errorCode, message, cause, namespace, code, version, alias);
        }
    }
}
