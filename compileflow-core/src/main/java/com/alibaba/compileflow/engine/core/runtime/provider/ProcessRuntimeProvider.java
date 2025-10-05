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
package com.alibaba.compileflow.engine.core.runtime.provider;

import com.alibaba.compileflow.engine.ProcessSource;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntime;

/**
 * Provides stale-while-revalidate runtime acquisition backed by cache and compilation service.
 *
 * @author yusu
 */
public interface ProcessRuntimeProvider {

    /**
     * Returns a ready-to-execute {@link ProcessRuntime} for the given source and classloader.
     * Implements SWR: cache hit returns immediately; digest change triggers background recompile
     * while returning current runtime; cold start compiles synchronously with single-flight.
     */
    ProcessRuntime getRuntime(ClassLoader cl, ProcessSource processSource);
}


