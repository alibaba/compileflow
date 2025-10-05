/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.config;

/**
 * Configuration keys for CompileFlow framework.
 * <p>
 * All configuration keys follow the naming convention: {@code compileflow.module.sub-module.property-name}
 *
 * @author yusu
 */
public final class ProcessPropertyKeys {

    public static final class Cache {

        /**
         * Maximum size of the runtime cache.
         */
        public static final String RUNTIME_MAX_SIZE = "compileflow.cache.runtime-max-size";

        /**
         * Maximum size of the compile future cache.
         */
        public static final String COMPILE_FUTURES_MAX_SIZE = "compileflow.cache.compile-futures-max-size";

        /**
         * Maximum size of the dynamic class cache.
         */
        public static final String DYNAMIC_CLASS_MAX_SIZE = "compileflow.cache.dynamic-class-max-size";

        /**
         * Expiration time for the dynamic class cache in minutes.
         */
        public static final String DYNAMIC_CLASS_EXPIRE_MINUTES = "compileflow.cache.dynamic-class-expire-minutes";

        /**
         * Maximum size of the dynamic class compilation future cache.
         */
        public static final String DYNAMIC_FUTURES_MAX_SIZE = "compileflow.cache.dynamic-futures-max-size";

        /**
         * Maximum weight of the dynamic source code digest cache in characters.
         */
        public static final String DYNAMIC_DIGEST_MAX_WEIGHT = "compileflow.cache.dynamic-digest-max-weight-chars";

        /**
         * Expiration time for the dynamic source code digest cache in minutes.
         */
        public static final String DYNAMIC_DIGEST_EXPIRE_MINUTES = "compileflow.cache.dynamic-digest-expire-minutes";

        /**
         * Threshold in characters to skip digest caching for large source codes.
         */
        public static final String DYNAMIC_DIGEST_SKIP_THRESHOLD = "compileflow.cache.dynamic-digest-skip-threshold";

        /**
         * Maximum size of the data type cache.
         */
        public static final String DATATYPE_MAX_SIZE = "compileflow.cache.datatype-max-size";

        /**
         * Flag to enable or disable statistics for the data type cache.
         */
        public static final String DATATYPE_STATS_ENABLED = "compileflow.cache.datatype-stats-enabled";

        private Cache() {

        }
    }

    public static final class Executor {

        /**
         * Keep-alive time for compilation executor threads in seconds.
         */
        public static final String COMPILATION_KEEP_ALIVE_SECONDS = "compileflow.executor.compilation.keep-alive-seconds";

        /**
         * Queue size for the compilation executor.
         */
        public static final String COMPILATION_QUEUE_SIZE = "compileflow.executor.compilation.queue-size";

        /**
         * Keep-alive time for execution executor threads in seconds.
         */
        public static final String EXECUTION_KEEP_ALIVE_SECONDS = "compileflow.executor.execution.keep-alive-seconds";

        /**
         * Queue size for the execution executor.
         */
        public static final String EXECUTION_QUEUE_SIZE = "compileflow.executor.execution.queue-size";

        /**
         * Multiplier for the maximum number of threads in the execution executor.
         */
        public static final String EXECUTION_MAX_THREADS_MULTIPLIER = "compileflow.executor.execution.max-threads-multiplier";

        /**
         * Keep-alive time for event executor threads in seconds.
         */
        public static final String EVENT_KEEP_ALIVE_SECONDS = "compileflow.executor.event.keep-alive-seconds";

        /**
         * Queue size for the event executor.
         */
        public static final String EVENT_QUEUE_SIZE = "compileflow.executor.event.queue-size";

        /**
         * Multiplier for the maximum number of threads in the event executor.
         */
        public static final String EVENT_MAX_THREADS_MULTIPLIER = "compileflow.executor.event.max-threads-multiplier";

        /**
         * Number of threads for event handling.
         */
        public static final String EVENT_THREADS = "compileflow.executor.event-threads";

        /**
         * Number of threads for scheduled tasks.
         */
        public static final String SCHEDULE_THREADS = "compileflow.executor.schedule-threads";

        /**
         * Threads for compilation executor (coarse-grained thread count override).
         */
        public static final String COMPILATION_THREADS = "compileflow.executor.compilation-threads";

        /**
         * Threads for execution executor (coarse-grained thread count override).
         */
        public static final String EXECUTION_THREADS = "compileflow.executor.execution-threads";

        private Executor() {

        }
    }

    public static final class Compilation {

        /**
         * Whether to enable parallel compilation.
         */
        public static final String PARALLEL_COMPILATION_ENABLED = "compileflow.compilation.parallel-enabled";

        /**
         * Compilation timeout in seconds.
         */
        public static final String TIMEOUT_SECONDS = "compileflow.compilation.timeout-seconds";

        /**
         * Maximum number of compilation attempts.
         */
        public static final String MAX_ATTEMPTS = "compileflow.compilation.max-attempts";

        /**
         * Initial backoff delay for retries in milliseconds.
         */
        public static final String INITIAL_BACKOFF_MS = "compileflow.compilation.initial-backoff-ms";

        /**
         * Maximum backoff delay for retries in milliseconds.
         */
        public static final String MAX_BACKOFF_MS = "compileflow.compilation.max-backoff-ms";

        private Compilation() {

        }
    }

    public static final class ClassLoader {

        /**
         * Maintenance interval for the class loader in minutes.
         */
        public static final String MAINTENANCE_INTERVAL_MINUTES = "compileflow.class-loader.maintenance-interval-minutes";

        private ClassLoader() {

        }
    }

    public static final class Observability {

        /**
         * Enable or disable observability features (metrics, events, tracing).
         */
        public static final String ENABLED = "compileflow.observability.enabled";

        /**
         * Enable or disable metrics collection.
         */
        public static final String METRICS_ENABLED = "compileflow.observability.metrics-enabled";

        /**
         * Enable or disable asynchronous event processing.
         */
        public static final String EVENTS_ASYNC = "compileflow.observability.events-async";

        private Observability() {

        }
    }
}
