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

import com.alibaba.compileflow.engine.core.extension.ExtensionContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable context passed to {@link VersionSelector} for deciding the version.
 * minimal fields to avoid overdesign.
 *
 * @author yusu
 */
public final class VersionSelectContext implements ExtensionContext {

    private final String processCode;
    private final Map<String, Object> context;

    private VersionSelectContext(Builder builder) {
        this.processCode = builder.processCode;
        this.context = Collections.unmodifiableMap(new HashMap<>(builder.context));
    }

    public static Builder builder(String processCode) {
        return new Builder(processCode);
    }

    public String getProcessCode() {
        return processCode;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public static final class Builder {
        private final String processCode;
        private Map<String, Object> context = new HashMap<>();

        private Builder(String processCode) {
            this.processCode = Objects.requireNonNull(processCode, "processCode");
        }

        public Builder context(Map<String, Object> ctx) {
            this.context = ctx != null ? new HashMap<>(ctx) : new HashMap<>();
            return this;
        }

        public VersionSelectContext build() {
            return new VersionSelectContext(this);
        }
    }
}


