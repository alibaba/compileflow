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
 * Default values for CompileFlow configuration properties.
 *
 * @author yusu
 */
public final class ProcessPropertyDefaults {

    private ProcessPropertyDefaults() {

    }

    public static final class Cache {

        /**
         * Default maximum size of the runtime cache.
         */
        public static final int RUNTIME_MAX_SIZE = 2048;

        /**
         * Default maximum size of the compile future cache.
         */
        public static final int COMPILE_FUTURES_MAX_SIZE = 2048;

        /**
         * Default maximum size of the dynamic class cache.
         */
        public static final int DYNAMIC_CLASS_MAX_SIZE = 512;

        /**
         * Default expiration time for the dynamic class cache in minutes.
         */
        public static final int DYNAMIC_CLASS_EXPIRE_MINUTES = 30;

        /**
         * Default maximum size of the dynamic class compilation future cache.
         */
        public static final int DYNAMIC_FUTURES_MAX_SIZE = 1024;

        /**
         * Default maximum weight of the dynamic source code digest cache in characters (32MB).
         */
        public static final int DYNAMIC_DIGEST_MAX_WEIGHT = 32 * 1024 * 1024;

        /**
         * Default expiration time for the dynamic source code digest cache in minutes.
         */
        public static final int DYNAMIC_DIGEST_EXPIRE_MINUTES = 30;

        /**
         * Default threshold in characters to skip digest caching for large source codes (512KB).
         */
        public static final int DYNAMIC_DIGEST_SKIP_THRESHOLD = 512 * 1024;

        /**
         * Default maximum size of the data type cache.
         */
        public static final int DATATYPE_MAX_SIZE = 2048;

        /**
         * Default setting for enabling or disabling data type cache statistics.
         */
        public static final boolean DATATYPE_STATS_ENABLED = false;

        private Cache() {

        }
    }

    public static final class Executor {

        /**
         * Default keep-alive time for compilation executor threads in seconds.
         */
        public static final long COMPILATION_KEEP_ALIVE_SECONDS = 60L;

        /**
         * Default queue size for the compilation executor.
         */
        public static final int COMPILATION_QUEUE_SIZE = 4;

        /**
         * Default keep-alive time for execution executor threads in seconds.
         */
        public static final long EXECUTION_KEEP_ALIVE_SECONDS = 180L;

        /**
         * Default queue size for the execution executor.
         */
        public static final int EXECUTION_QUEUE_SIZE = 32;

        /**
         * Default multiplier for the maximum number of threads in the execution executor.
         */
        public static final int EXECUTION_MAX_THREADS_MULTIPLIER = 2;

        /**
         * Default keep-alive time for event executor threads in seconds.
         */
        public static final long EVENT_KEEP_ALIVE_SECONDS = 120L;

        /**
         * Default queue size for the event executor.
         */
        public static final int EVENT_QUEUE_SIZE = 16;

        /**
         * Default multiplier for the maximum number of threads in the event executor.
         */
        public static final int EVENT_MAX_THREADS_MULTIPLIER = 2;

        /**
         * Default number of threads for event handling.
         */
        public static final int EVENT_THREADS = 2;

        /**
         * Divisor for calculating the default number of schedule threads (CPUs / 8).
         */
        public static final int SCHEDULE_THREADS_DIVISOR = 8;
        /**
         * Minimum number of schedule threads.
         */
        public static final int SCHEDULE_THREADS_MIN = 2;

        private Executor() {

        }
    }

    public static final class Compilation {

        /**
         * Default setting for enabling or disabling parallel compilation.
         */
        public static final boolean PARALLEL_COMPILATION_ENABLED = true;

        /**
         * Default compilation timeout in seconds.
         */
        public static final long TIMEOUT_SECONDS = 10L;

        /**
         * Default maximum number of compilation attempts.
         */
        public static final int MAX_ATTEMPTS = 1;

        /**
         * Default initial backoff delay for retries in milliseconds.
         */
        public static final long INITIAL_BACKOFF_MS = 10L;

        /**
         * Default maximum backoff delay for retries in milliseconds.
         */
        public static final long MAX_BACKOFF_MS = 100L;

        private Compilation() {

        }
    }

    public static final class ClassLoader {

        /**
         * Default maintenance interval for the class loader in minutes.
         */
        public static final int MAINTENANCE_INTERVAL_MINUTES = 20;

        private ClassLoader() {

        }
    }

    public static final class Observability {

        /**
         * Default setting for enabling or disabling observability features.
         */
        public static final boolean ENABLED = false;

        /**
         * Default setting for enabling or disabling metrics collection.
         */
        public static final boolean METRICS_ENABLED = true;

        /**
         * Default setting for enabling or disabling asynchronous event processing.
         */
        public static final boolean EVENTS_ASYNC = true;

        private Observability() {

        }
    }

}
