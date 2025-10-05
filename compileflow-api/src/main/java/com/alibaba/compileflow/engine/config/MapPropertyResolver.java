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

import java.util.Map;
import java.util.Optional;

/**
 * Simple in-memory {@link PropertyResolver} backed by a key-value map of strings.
 * Useful for programmatically supplying highest-precedence properties.
 *
 * @author yusu
 */
public final class MapPropertyResolver implements PropertyResolver {

    private final Map<String, String> properties;

    public MapPropertyResolver(Map<String, String> properties) {
        this.properties = properties;
    }

    @Override
    public String getString(String key, String defaultValue) {
        return Optional.ofNullable(properties).map(properties -> properties.get(key)).orElse(defaultValue);
    }

    @Override
    public int getInt(String key, int defaultValue) {
        String v = getString(key, null);
        if (v == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public long getLong(String key, long defaultValue) {
        String v = getString(key, null);
        if (v == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        String v = getString(key, null);
        return v != null ? Boolean.parseBoolean(v) : defaultValue;
    }
}


