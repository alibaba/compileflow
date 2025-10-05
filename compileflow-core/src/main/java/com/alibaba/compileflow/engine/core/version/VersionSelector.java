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
package com.alibaba.compileflow.engine.core.version;

import com.alibaba.compileflow.engine.core.extension.Extension;
import com.alibaba.compileflow.engine.core.extension.ExtensionPoint;

/**
 * Resolves the effective process version given a decision context.
 * Implementations must be fast, pure (no remote I/O), and thread-safe.
 *
 * @author yusu
 */
public interface VersionSelector extends Extension<VersionSelectContext> {

    String EXT_VERSION_RESOLVER_CODE = "com.alibaba.compileflow.engine.core.version.VersionSelector.selectVersion";

    /**
     * Resolve the effective version for the given context.
     *
     * @param ctx non-null version decision context
     * @return version string or {@code null} to indicate using active/default version
     */
    @ExtensionPoint(code = EXT_VERSION_RESOLVER_CODE)
    String selectVersion(VersionSelectContext ctx);

}


