/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.core.runtime.cache;

import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeWrapper;

/**
 * Cache facade mapping process code to {@link ProcessRuntimeWrapper}.
 * Minimal surface; CAS on install to avoid stale overrides. Thread-safe.
 *
 * @author yusu
 */
public interface ProcessRuntimeCache {

    /**
     * Returns the wrapper for the specified process code if present, otherwise {@code null}.
     *
     * @param code process code (non-null)
     * @return the wrapper or {@code null}
     */
    ProcessRuntimeWrapper getIfPresent(String code);

    /**
     * Install the {@code newWrapper} only if the current cache entry is empty or its digest equals
     * the {@code expectedDigest}. If another thread has already installed a newer version,
     * this method must keep the current entry unchanged and discard {@code newWrapper}.
     *
     * @param code           process code
     * @param expectedDigest digest observed before compilation; may be {@code null}
     * @param newWrapper     new compiled runtime wrapper (non-null)
     */
    void compareAndInstall(String code, String expectedDigest, ProcessRuntimeWrapper newWrapper);

    /**
     * Invalidate all entries and release associated resources.
     */
    void invalidateAll();

    /**
     * Returns the number of cached entries.
     */
    long size();
}


