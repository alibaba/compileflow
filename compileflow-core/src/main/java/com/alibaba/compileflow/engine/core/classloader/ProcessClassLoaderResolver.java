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
package com.alibaba.compileflow.engine.core.classloader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A utility for resolving the most appropriate {@link ClassLoader} to be used as
 * the parent for dynamically compiled process classes.
 * <p>
 * This resolver follows a specific priority chain to find a suitable ClassLoader,
 * ensuring that process dependencies can be found in various environments (e.g.,
 * standard Java applications, web containers, OSGi).
 * <p>
 * The resolution order is:
 * <ol>
 *   <li>An explicitly provided ClassLoader.</li>
 *   <li>The ClassLoader of a provided referrer class (if any).</li>
 *   <li>The current Thread's Context ClassLoader (TCCL).</li>
 *   <li>The ClassLoader that loaded this resolver class.</li>
 *   <li>The System ClassLoader (as a final fallback).</li>
 * </ol>
 *
 * @author yusu
 */
public class ProcessClassLoaderResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessClassLoaderResolver.class);

    public static ClassLoader resolveEffectiveClassLoader(ClassLoader provided) {
        return resolveEffectiveClassLoader(provided, null);
    }

    /**
     * Resolves the effective ClassLoader based on the defined priority order.
     *
     * @param provided The ClassLoader explicitly provided by the caller (can be null).
     * @param referrer A class whose ClassLoader should be considered (can be null).
     * @return The most suitable non-null {@link ClassLoader} found.
     */
    public static ClassLoader resolveEffectiveClassLoader(ClassLoader provided, Class<?> referrer) {
        // 1. Use the explicitly provided ClassLoader if available.
        if (provided != null) {
            LOGGER.debug("Using provided ClassLoader: {}", provided.getClass().getName());
            return provided;
        }

        // 2. Use the ClassLoader of the referrer class, if provided.
        if (referrer != null) {
            ClassLoader referrerCL = referrer.getClassLoader();
            if (referrerCL != null) {
                LOGGER.debug("Using referrer ClassLoader: {} from {}",
                        referrerCL.getClass().getName(), referrer.getName());
                return referrerCL;
            }
        }

        // 3. Use the Thread Context ClassLoader, which is often set by containers.
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        if (tccl != null) {
            LOGGER.debug("Using Thread Context ClassLoader: {}", tccl.getClass().getName());
            return tccl;
        }

        // 4. Use the ClassLoader that loaded this resolver class.
        ClassLoader resolverCL = ProcessClassLoaderResolver.class.getClassLoader();
        if (resolverCL != null) {
            LOGGER.debug("Using resolver's ClassLoader: {}", resolverCL.getClass().getName());
            return resolverCL;
        }

        // 5. As a final fallback, use the system ClassLoader.
        ClassLoader systemCL = ClassLoader.getSystemClassLoader();
        LOGGER.warn("Falling back to system ClassLoader: {}",
                systemCL != null ? systemCL.getClass().getName() : "null");
        return systemCL;
    }

}
