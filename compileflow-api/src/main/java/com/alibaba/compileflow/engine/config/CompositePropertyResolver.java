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

import java.util.Arrays;
import java.util.List;

/**
 * A {@link PropertyResolver} that tries a list of resolvers in order
 * until one returns a non-null string value.
 *
 * @author yusu
 */
public final class CompositePropertyResolver implements PropertyResolver {

    private final List<PropertyResolver> delegates;

    public CompositePropertyResolver(PropertyResolver... delegates) {
        this.delegates = Arrays.asList(delegates);
    }

    @Override
    public String getString(String key, String defaultValue) {
        for (PropertyResolver resolver : delegates) {
            String v = resolver.getString(key, null);
            if (v != null) {
                return v;
            }
        }
        return defaultValue;
    }

    @Override
    public int getInt(String key, int defaultValue) {
        String s = getString(key, null);
        if (s == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ignore) {
            return defaultValue;
        }
    }

    @Override
    public long getLong(String key, long defaultValue) {
        String s = getString(key, null);
        if (s == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException ignore) {
            return defaultValue;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        String s = getString(key, null);
        return s != null ? Boolean.parseBoolean(s) : defaultValue;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("CompositePropertyResolver[");
        for (int i = 0; i < delegates.size(); i++) {
            sb.append(delegates.get(i).getClass().getSimpleName());
            if (i < delegates.size() - 1) {
                sb.append(" -> ");
            }
        }
        return sb.append(']').toString();
    }
}


