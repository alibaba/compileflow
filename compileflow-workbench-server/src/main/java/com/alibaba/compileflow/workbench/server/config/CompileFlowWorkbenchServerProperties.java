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
package com.alibaba.compileflow.workbench.server.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Immutable configuration snapshot for CompileFlow Workbench Server.
 *
 * @author yusu
 */
@ConfigurationProperties(prefix = "compileflow.workbench.server", ignoreUnknownFields = false)
@Validated
public final class CompileFlowWorkbenchServerProperties {
    /**
     * Server authentication policy and credentials.
     */
    @Valid
    @NotNull
    private final Authentication authentication;
    /**
     * HTTP request resource limits applied before body deserialization.
     */
    @Valid
    @NotNull
    private final Http http;
    /**
     * Database schema migration and admission policy.
     */
    @Valid
    @NotNull
    private final Database database;
    /**
     * Explicit draft execution policy.
     */
    @Valid
    @NotNull
    private final PreviewExecution previewExecution;
    /**
     * Execution-log query resource limits.
     */
    @Valid
    @NotNull
    private final ExecutionLog executionLog;
    /**
     * Persisted asynchronous invocation worker policy.
     */
    @Valid
    @NotNull
    private final AsyncInvocation asyncInvocation;

    /**
     * Creates an immutable server configuration snapshot.
     *
     * @param authentication   server authentication policy and credentials
     * @param http             HTTP request resource limits
     * @param database         database schema migration and admission policy
     * @param previewExecution explicit draft execution policy
     * @param executionLog     execution-log query resource limits
     * @param asyncInvocation  persisted asynchronous worker policy
     */
    public CompileFlowWorkbenchServerProperties(@DefaultValue Authentication authentication, @DefaultValue Http http,
            @DefaultValue Database database, @DefaultValue PreviewExecution previewExecution,
            @DefaultValue ExecutionLog executionLog, @DefaultValue AsyncInvocation asyncInvocation) {
        this.authentication = authentication;
        this.http = http;
        this.database = database;
        this.previewExecution = previewExecution;
        this.executionLog = executionLog;
        this.asyncInvocation = asyncInvocation;
    }

    private static boolean isPositiveWholeMilliseconds(Duration duration) {
        return duration != null && duration.compareTo(Duration.ofMillis(1)) >= 0
                && duration.compareTo(Duration.ofMillis(Long.MAX_VALUE)) <= 0 && duration.getNano() % 1_000_000 == 0;
    }

    public Authentication getAuthentication() {
        return authentication;
    }

    public Http getHttp() {
        return http;
    }

    public Database getDatabase() {
        return database;
    }

    public PreviewExecution getPreviewExecution() {
        return previewExecution;
    }

    public ExecutionLog getExecutionLog() {
        return executionLog;
    }

    public AsyncInvocation getAsyncInvocation() {
        return asyncInvocation;
    }

    /**
     * Authentication mechanism applied to non-public HTTP endpoints.
     */
    public enum AuthenticationMode {
        /**
         * Require the configured shared API key.
         */
        API_KEY,
        /**
         * Disable built-in authentication for explicit development or test use only.
         */
        DISABLED
    }

    /**
     * Server authentication settings.
     */
    public static final class Authentication {
        private static final int MIN_API_KEY_LENGTH = 32;
        private static final int MAX_API_KEY_LENGTH = 256;
        /**
         * Authentication mechanism; defaults to fail-closed API-key authentication.
         */
        @NotNull
        private final AuthenticationMode mode;
        /**
         * Shared 32..256 character URL-safe API key used when mode is {@code API_KEY}.
         */
        private final String apiKey;
        /**
         * Stable actor identity represented by the configured authentication mode.
         */
        private final String servicePrincipal;

        public Authentication(@DefaultValue("API_KEY") AuthenticationMode mode, String apiKey, String servicePrincipal) {
            this.mode = mode;
            this.apiKey = apiKey == null ? "" : apiKey;
            this.servicePrincipal = servicePrincipal == null ? "" : servicePrincipal;
        }

        private static boolean isUrlSafeCredentialCharacter(char value) {
            return (value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z') || (value >= '0' && value <= '9')
                    || value == '.' || value == '_' || value == '~' || value == '-';
        }

        public AuthenticationMode getMode() {
            return mode;
        }

        public String getApiKey() {
            return apiKey;
        }

        public String getServicePrincipal() {
            return servicePrincipal;
        }

        @AssertTrue(message = "compileflow.workbench.server.authentication.api-key must contain 32..256 URL-safe "
                + "ASCII characters (A-Z, a-z, 0-9, '.', '_', '~', or '-') when set")
        public boolean isApiKeyValid() {
            if (apiKey.isEmpty()) {
                return true;
            }
            if (apiKey.length() < MIN_API_KEY_LENGTH || apiKey.length() > MAX_API_KEY_LENGTH) {
                return false;
            }
            for (int i = 0; i < apiKey.length(); i++) {
                char value = apiKey.charAt(i);
                if (!isUrlSafeCredentialCharacter(value)) {
                    return false;
                }
            }
            return true;
        }

        @AssertTrue(message = "compileflow.workbench.server.authentication.api-key is required in API_KEY mode and "
                + "must be absent in DISABLED mode")
        public boolean isModeConsistent() {
            if (mode == null) {
                return false;
            }
            return switch (mode) {
                case API_KEY -> !apiKey.isEmpty();
                case DISABLED -> apiKey.isEmpty();
            };
        }

        @AssertTrue(message = "compileflow.workbench.server.authentication.service-principal must contain 1..128 "
                + "visible ASCII characters without whitespace")
        public boolean isServicePrincipalValid() {
            if (servicePrincipal.isEmpty() || servicePrincipal.length() > 128) {
                return false;
            }
            for (int i = 0; i < servicePrincipal.length(); i++) {
                char value = servicePrincipal.charAt(i);
                if (value < 0x21 || value > 0x7e) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * HTTP request resource limits.
     */
    public static final class Http {
        private static final DataSize MAX_REQUEST_SIZE = DataSize.ofMegabytes(100);
        /**
         * Maximum body size accepted from one HTTP request.
         */
        @NotNull
        private final DataSize maxRequestSize;

        public Http(@DefaultValue("10MB") DataSize maxRequestSize) {
            this.maxRequestSize = maxRequestSize;
        }

        public DataSize getMaxRequestSize() {
            return maxRequestSize;
        }

        @AssertTrue(message = "compileflow.workbench.server.http.max-request-size must be between 1 byte and 100MB")
        public boolean isMaxRequestSizeValid() {
            return maxRequestSize != null && maxRequestSize.toBytes() > 0
                    && maxRequestSize.compareTo(MAX_REQUEST_SIZE) <= 0;
        }
    }

    /**
     * Database schema migration and admission settings.
     */
    public static final class Database {
        /**
         * Selected database Provider for Workbench-owned drafts, execution logs, and asynchronous invocations.
         */
        public enum Provider {
            POSTGRESQL,
            MYSQL
        }

        /**
         * Selected database Provider; the executable must contain the matching provider-specific dependencies.
         */
        private final Provider provider;
        /**
         * Whether this Server process may apply the packaged Flyway migrations.
         */
        private final boolean migrate;

        public Database(@DefaultValue("POSTGRESQL") Provider provider, @DefaultValue("false") boolean migrate) {
            this.provider = provider;
            this.migrate = migrate;
        }

        public Provider getProvider() {
            return provider;
        }

        public boolean isMigrate() {
            return migrate;
        }
    }

    /**
     * Policy for executing unpersisted definitions on the Workbench Server.
     */
    public static final class PreviewExecution {
        /**
         * Whether the trusted draft execution endpoint is available.
         */
        private final boolean enabled;

        public PreviewExecution(@DefaultValue("false") boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isEnabled() {
            return enabled;
        }
    }

    /**
     * Resource policy for complete execution-log reads.
     */
    public static final class ExecutionLog {
        /**
         * Maximum rows loaded by one complete export or in-memory aggregate query.
         */
        private final int maxQueryRows;
        /**
         * Maximum rows removed by one retention request.
         */
        private final int purgeBatchSize;

        public ExecutionLog(@DefaultValue("10000") int maxQueryRows, @DefaultValue("1000") int purgeBatchSize) {
            this.maxQueryRows = maxQueryRows;
            this.purgeBatchSize = purgeBatchSize;
        }

        public int getMaxQueryRows() {
            return maxQueryRows;
        }

        public int getPurgeBatchSize() {
            return purgeBatchSize;
        }

        @AssertTrue(message = "compileflow.workbench.server.execution-log.max-query-rows must be between 1 and 100000")
        public boolean isMaxQueryRowsValid() {
            return maxQueryRows >= 1 && maxQueryRows <= 100_000;
        }

        @AssertTrue(message = "compileflow.workbench.server.execution-log.purge-batch-size must be between 1 and 10000")
        public boolean isPurgeBatchSizeValid() {
            return purgeBatchSize >= 1 && purgeBatchSize <= 10_000;
        }
    }

    /**
     * Persisted async invocation worker settings.
     */
    public static final class AsyncInvocation {
        private static final int MAX_CONCURRENCY = 256;
        /**
         * Maximum number of concurrent asynchronous invocations.
         */
        @Min(value = 1, message = "concurrency must be between 1 and 256")
        @Max(value = MAX_CONCURRENCY, message = "concurrency must be between 1 and 256")
        private final int concurrency;
        /**
         * Delay between persisted invocation dispatch cycles.
         */
        @NotNull
        private final Duration dispatchInterval;
        /**
         * Ownership lease duration for a running invocation attempt.
         */
        @NotNull
        private final Duration leaseDuration;
        /**
         * Delay between scans for abandoned leases.
         */
        @NotNull
        private final Duration leaseRecoveryInterval;

        public AsyncInvocation(@DefaultValue("4") int concurrency, @DefaultValue("1s") Duration dispatchInterval,
                @DefaultValue("30s") Duration leaseDuration, @DefaultValue("5s") Duration leaseRecoveryInterval) {
            this.concurrency = concurrency;
            this.dispatchInterval = dispatchInterval;
            this.leaseDuration = leaseDuration;
            this.leaseRecoveryInterval = leaseRecoveryInterval;
        }

        public int getConcurrency() {
            return concurrency;
        }

        public Duration getDispatchInterval() {
            return dispatchInterval;
        }

        public Duration getLeaseDuration() {
            return leaseDuration;
        }

        public Duration getLeaseRecoveryInterval() {
            return leaseRecoveryInterval;
        }

        public Duration leaseRenewalDelay() {
            long leaseMillis = leaseDuration.toMillis();
            return Duration.ofMillis(Math.max(1L, leaseMillis / 3L));
        }

        @AssertTrue(message = "compileflow.workbench.server.async-invocation intervals must be positive whole-"
                + "millisecond durations representable as a long")
        public boolean isIntervalsValid() {
            return isPositiveWholeMilliseconds(dispatchInterval) && isPositiveWholeMilliseconds(leaseDuration)
                    && isPositiveWholeMilliseconds(leaseRecoveryInterval);
        }

        @AssertTrue(message = "compileflow.workbench.server.async-invocation.lease-duration must be at least 2ms")
        public boolean isLeaseDurationRenewable() {
            return leaseDuration != null && isPositiveWholeMilliseconds(leaseDuration)
                    && leaseDuration.compareTo(Duration.ofMillis(2)) >= 0;
        }
    }
}
