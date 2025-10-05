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
package com.alibaba.compileflow.engine.spring.boot.autoconfigure;

import com.alibaba.compileflow.engine.config.PropertyResolver;
import org.springframework.core.env.Environment;

/**
 * Spring Environment backed {@link PropertyResolver} implementation.
 *
 * @author yusu
 */
final class SpringEnvironmentPropertyResolver implements PropertyResolver {

    private final Environment environment;

    SpringEnvironmentPropertyResolver(Environment environment) {
        this.environment = environment;
    }

    @Override
    public String getString(String key, String defaultValue) {
        String v = environment.getProperty(key);
        return v != null ? v : defaultValue;
    }

    @Override
    public int getInt(String key, int defaultValue) {
        Integer v = environment.getProperty(key, Integer.class);
        return v != null ? v : defaultValue;
    }

    @Override
    public long getLong(String key, long defaultValue) {
        Long v = environment.getProperty(key, Long.class);
        return v != null ? v : defaultValue;
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        Boolean v = environment.getProperty(key, Boolean.class);
        return v != null ? v : defaultValue;
    }
}


