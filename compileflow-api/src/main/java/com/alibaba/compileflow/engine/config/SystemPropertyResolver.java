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
 * Default {@link PropertyResolver} backed by JVM system properties with
 * fallbacks to environment variables for string lookups.
 *
 * @author yusu
 */
public final class SystemPropertyResolver implements PropertyResolver {

    @Override
    public String getString(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value == null) {
            // best-effort: try environment variable by converting dots to underscores and upper-casing
            String envKey = key.replace('.', '_').toUpperCase();
            value = System.getenv(envKey);
        }
        return value != null ? value : defaultValue;
    }

    @Override
    public int getInt(String key, int defaultValue) {
        String s = System.getProperty(key);
        if (s == null) {
            String envKey = key.replace('.', '_').toUpperCase();
            s = System.getenv(envKey);
        }
        if (s == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public long getLong(String key, long defaultValue) {
        String s = System.getProperty(key);
        if (s == null) {
            String envKey = key.replace('.', '_').toUpperCase();
            s = System.getenv(envKey);
        }
        if (s == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        String v = System.getProperty(key);
        if (v == null) {
            String envKey = key.replace('.', '_').toUpperCase();
            String env = System.getenv(envKey);
            if (env != null) {
                return Boolean.parseBoolean(env);
            }
            return defaultValue;
        }
        return Boolean.parseBoolean(v);
    }
}


