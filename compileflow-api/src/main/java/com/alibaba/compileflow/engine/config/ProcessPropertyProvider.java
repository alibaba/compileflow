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

import org.apache.commons.lang3.StringUtils;

/**
 * Simplified type-safe access to CompileFlow configuration properties.
 * <p>
 * Configuration priority (simplified):
 * 1. Programmatic API (ProcessEngineConfig.builder())
 * 2. Spring Boot Configuration (application.yml)
 * 3. System Properties (-Dcompileflow.*)
 * 4. Defaults (ProcessPropertyDefaults)
 *
 * @author yusu
 */
public final class ProcessPropertyProvider {

    private static volatile PropertyResolver RESOLVER = new SystemPropertyResolver();

    private ProcessPropertyProvider() {
    }

    /**
     * Install a custom property resolver.
     * Called by Spring Boot auto-configuration before engine initialization.
     */
    public static void install(PropertyResolver resolver) {
        if (resolver != null) {
            RESOLVER = resolver;
        }
    }

    // Helper methods (keep public for DataType compatibility)
    private static int getInt(String key, int defaultValue) {
        return RESOLVER.getInt(key, defaultValue);
    }

    private static long getLong(String key, long defaultValue) {
        return RESOLVER.getLong(key, defaultValue);
    }

    private static String getString(String key, String defaultValue) {
        return RESOLVER.getString(key, defaultValue);
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        return RESOLVER.getBoolean(key, defaultValue);
    }

    public static final class Cache {

        private Cache() {
        }

        public static int getRuntimeMaxSize() {
            return getInt(ProcessPropertyKeys.Cache.RUNTIME_MAX_SIZE, ProcessPropertyDefaults.Cache.RUNTIME_MAX_SIZE);
        }

        public static int getCompileFuturesMaxSize() {
            return getInt(ProcessPropertyKeys.Cache.COMPILE_FUTURES_MAX_SIZE, ProcessPropertyDefaults.Cache.COMPILE_FUTURES_MAX_SIZE);
        }

        public static int getDynamicClassMaxSize() {
            return getInt(ProcessPropertyKeys.Cache.DYNAMIC_CLASS_MAX_SIZE, ProcessPropertyDefaults.Cache.DYNAMIC_CLASS_MAX_SIZE);
        }

        public static int getDynamicClassExpireMinutes() {
            return getInt(ProcessPropertyKeys.Cache.DYNAMIC_CLASS_EXPIRE_MINUTES, ProcessPropertyDefaults.Cache.DYNAMIC_CLASS_EXPIRE_MINUTES);
        }

        public static int getDynamicFuturesMaxSize() {
            return getInt(ProcessPropertyKeys.Cache.DYNAMIC_FUTURES_MAX_SIZE, ProcessPropertyDefaults.Cache.DYNAMIC_FUTURES_MAX_SIZE);
        }

        public static int getDynamicDigestMaxWeight() {
            return getInt(ProcessPropertyKeys.Cache.DYNAMIC_DIGEST_MAX_WEIGHT, ProcessPropertyDefaults.Cache.DYNAMIC_DIGEST_MAX_WEIGHT);
        }

        public static int getDynamicDigestExpireMinutes() {
            return getInt(ProcessPropertyKeys.Cache.DYNAMIC_DIGEST_EXPIRE_MINUTES, ProcessPropertyDefaults.Cache.DYNAMIC_DIGEST_EXPIRE_MINUTES);
        }

        public static int getDynamicDigestSkipThreshold() {
            return getInt(ProcessPropertyKeys.Cache.DYNAMIC_DIGEST_SKIP_THRESHOLD, ProcessPropertyDefaults.Cache.DYNAMIC_DIGEST_SKIP_THRESHOLD);
        }

        public static int getDataTypeMaxSize() {
            return getInt(ProcessPropertyKeys.Cache.DATATYPE_MAX_SIZE, ProcessPropertyDefaults.Cache.DATATYPE_MAX_SIZE);
        }

        public static boolean isDataTypeStatsEnabled() {
            return getBoolean(ProcessPropertyKeys.Cache.DATATYPE_STATS_ENABLED, ProcessPropertyDefaults.Cache.DATATYPE_STATS_ENABLED);
        }
    }

    public static final class Executor {

        private Executor() {
        }

        public static long getCompilationKeepAliveSeconds() {
            return getLong(ProcessPropertyKeys.Executor.COMPILATION_KEEP_ALIVE_SECONDS, ProcessPropertyDefaults.Executor.COMPILATION_KEEP_ALIVE_SECONDS);
        }

        public static int getCompilationQueueSize() {
            return getInt(ProcessPropertyKeys.Executor.COMPILATION_QUEUE_SIZE, ProcessPropertyDefaults.Executor.COMPILATION_QUEUE_SIZE);
        }

        public static long getExecutionKeepAliveSeconds() {
            return getLong(ProcessPropertyKeys.Executor.EXECUTION_KEEP_ALIVE_SECONDS, ProcessPropertyDefaults.Executor.EXECUTION_KEEP_ALIVE_SECONDS);
        }

        public static int getExecutionQueueSize() {
            return getInt(ProcessPropertyKeys.Executor.EXECUTION_QUEUE_SIZE, ProcessPropertyDefaults.Executor.EXECUTION_QUEUE_SIZE);
        }

        public static int getExecutionMaxThreadsMultiplier() {
            return getInt(ProcessPropertyKeys.Executor.EXECUTION_MAX_THREADS_MULTIPLIER, ProcessPropertyDefaults.Executor.EXECUTION_MAX_THREADS_MULTIPLIER);
        }

        public static long getEventKeepAliveSeconds() {
            return getLong(ProcessPropertyKeys.Executor.EVENT_KEEP_ALIVE_SECONDS, ProcessPropertyDefaults.Executor.EVENT_KEEP_ALIVE_SECONDS);
        }

        public static int getEventQueueSize() {
            return getInt(ProcessPropertyKeys.Executor.EVENT_QUEUE_SIZE, ProcessPropertyDefaults.Executor.EVENT_QUEUE_SIZE);
        }

        public static int getEventMaxThreadsMultiplier() {
            return getInt(ProcessPropertyKeys.Executor.EVENT_MAX_THREADS_MULTIPLIER, ProcessPropertyDefaults.Executor.EVENT_MAX_THREADS_MULTIPLIER);
        }

        public static int getEventThreads() {
            String value = getString(ProcessPropertyKeys.Executor.EVENT_THREADS, String.valueOf(ProcessPropertyDefaults.Executor.EVENT_THREADS));
            return StringUtils.isNumeric(value) ? Integer.parseInt(value) :
                    ProcessPropertyDefaults.Executor.EVENT_THREADS;
        }

        public static int getScheduleThreads() {
            String value = getString(ProcessPropertyKeys.Executor.SCHEDULE_THREADS, String.valueOf(calculateDefaultScheduleThreads()));
            if (StringUtils.isNumeric(value)) {
                return Integer.parseInt(value);
            }
            return calculateDefaultScheduleThreads();
        }

        private static int calculateDefaultScheduleThreads() {
            return Math.max(ProcessPropertyDefaults.Executor.SCHEDULE_THREADS_MIN,
                    Runtime.getRuntime().availableProcessors() / ProcessPropertyDefaults.Executor.SCHEDULE_THREADS_DIVISOR);
        }
    }

    public static final class Compilation {

        private Compilation() {
        }

        public static boolean isParallelCompilationEnabled() {
            return getBoolean(ProcessPropertyKeys.Compilation.PARALLEL_COMPILATION_ENABLED, ProcessPropertyDefaults.Compilation.PARALLEL_COMPILATION_ENABLED);
        }

        public static long getTimeoutSeconds() {
            return getLong(ProcessPropertyKeys.Compilation.TIMEOUT_SECONDS, ProcessPropertyDefaults.Compilation.TIMEOUT_SECONDS);
        }

        public static int getMaxAttempts() {
            return getInt(ProcessPropertyKeys.Compilation.MAX_ATTEMPTS, ProcessPropertyDefaults.Compilation.MAX_ATTEMPTS);
        }

        public static long getInitialBackoffMs() {
            return getLong(ProcessPropertyKeys.Compilation.INITIAL_BACKOFF_MS, ProcessPropertyDefaults.Compilation.INITIAL_BACKOFF_MS);
        }

        public static long getMaxBackoffMs() {
            return getLong(ProcessPropertyKeys.Compilation.MAX_BACKOFF_MS, ProcessPropertyDefaults.Compilation.MAX_BACKOFF_MS);
        }
    }

    public static final class ClassLoader {

        private ClassLoader() {
        }

        public static int getMaintenanceIntervalMinutes() {
            return getInt(ProcessPropertyKeys.ClassLoader.MAINTENANCE_INTERVAL_MINUTES, ProcessPropertyDefaults.ClassLoader.MAINTENANCE_INTERVAL_MINUTES);
        }
    }

    public static final class Observability {

        private Observability() {
        }

        public static boolean isEnabled() {
            return getBoolean(ProcessPropertyKeys.Observability.ENABLED, ProcessPropertyDefaults.Observability.ENABLED);
        }

        public static boolean isMetricsEnabled() {
            return getBoolean(ProcessPropertyKeys.Observability.METRICS_ENABLED, ProcessPropertyDefaults.Observability.METRICS_ENABLED);
        }

        public static boolean isEventsAsync() {
            return getBoolean(ProcessPropertyKeys.Observability.EVENTS_ASYNC, ProcessPropertyDefaults.Observability.EVENTS_ASYNC);
        }
    }
}
