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
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeWrapper;
import com.alibaba.compileflow.engine.core.runtime.RuntimeSpec;
import com.alibaba.compileflow.engine.core.runtime.cache.ProcessRuntimeCache;
import com.alibaba.compileflow.engine.core.runtime.compilation.CompilationService;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContextHolder;
import com.alibaba.compileflow.engine.core.version.DefaultVersionSelector;
import com.alibaba.compileflow.engine.core.version.VersionSelectContext;
import com.alibaba.compileflow.engine.core.version.VersionSelector;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Default RuntimeService preserving existing SWR and fallback semantics.
 *
 * @author yusu
 */
public class DefaultProcessRuntimeProvider implements ProcessRuntimeProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultProcessRuntimeProvider.class);

    private final ProcessRuntimeCache cache;
    private final CompilationService compilationService;
    private final VersionSelector versionSelector;

    public DefaultProcessRuntimeProvider(ProcessRuntimeCache cache, CompilationService compilationService) {
        this(cache, compilationService, new DefaultVersionSelector());
    }

    public DefaultProcessRuntimeProvider(ProcessRuntimeCache cache,
                                         CompilationService compilationService,
                                         VersionSelector versionSelector) {
        this.cache = cache;
        this.compilationService = compilationService;
        this.versionSelector = versionSelector == null ? new DefaultVersionSelector() : versionSelector;
    }

    @Override
    public ProcessRuntime getRuntime(ClassLoader cl, ProcessSource processSource) {
        final String code = Objects.requireNonNull(processSource.getCode(), "Process code must not be null");
        final String explicitVersion = StringUtils.trimToNull(processSource.getVersion());
        final String effectiveVersion;
        if (explicitVersion != null) {
            effectiveVersion = explicitVersion;
        } else {
            VersionSelectContext ctx = VersionSelectContext.builder(code)
                    .context(MapUtils.emptyIfNull(EngineExecutionContextHolder.isAvailable()
                            ? EngineExecutionContextHolder.current().processContext()
                            : null))
                    .build();
            effectiveVersion = versionSelector.selectVersion(ctx);
        }
        final String codeKey = effectiveVersion == null ? code : code + "#" + effectiveVersion;

        ProcessRuntimeWrapper current = cache.getIfPresent(codeKey);
        if (current != null && StringUtils.isBlank(processSource.getContent())) {
            return current.getRuntime();
        }

        // Build an effective source carrying the resolved version to keep digest stable
        ProcessSource effectiveSource = ProcessSource.of(code, effectiveVersion, processSource.getContent(), processSource.getLocator());
        String newDigest = RuntimeSpec.of(effectiveSource, cl).getDigest();

        if (current != null && Objects.equals(newDigest, current.getDigest())) {
            return current.getRuntime();
        }

        if (current != null) {
            compilationService.compileAsync(effectiveSource, cl, newDigest, current.getDigest());
            return current.getRuntime();
        }

        try {
            ProcessRuntimeWrapper built = compilationService.compileSync(effectiveSource, cl);
            return built.getRuntime();
        } catch (RuntimeException ex) {
            LOGGER.error("compile-failed code={} digest={} ver={} ", code, newDigest, effectiveVersion, ex);
            ProcessRuntimeWrapper fallback = cache.getIfPresent(codeKey);
            if (fallback != null) {
                LOGGER.warn("compile-fallback-to-cache code={} ver={} cacheDigest={} failedDigest={}",
                        code, effectiveVersion, fallback.getDigest(), newDigest);
                return fallback.getRuntime();
            }
            throw ex;
        }
    }
}


