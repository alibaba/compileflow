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
package com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.model;

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.util.ClassLoaderUtils;

import java.io.InputStream;

/**
 * @author wuxiang
 * @author yusu
 */
public class ResourceFlowStreamSource implements FlowStreamSource {

    private String resource;
    private ClassLoader classLoader;

    public static ResourceFlowStreamSource of(String resource) {
        ResourceFlowStreamSource resourceFlowStreamSource = new ResourceFlowStreamSource();
        resourceFlowStreamSource.setResource(resource);
        return resourceFlowStreamSource;
    }

    public static ResourceFlowStreamSource of(String resource, ClassLoader classLoader) {
        ResourceFlowStreamSource resourceFlowStreamSource = of(resource);
        resourceFlowStreamSource.setClassLoader(classLoader);
        return resourceFlowStreamSource;
    }

    private void setResource(String resource) {
        this.resource = resource;
    }

    private void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public InputStream getFlow() {
        InputStream inputStream = classLoader == null ? ClassLoaderUtils.getResourceAsStream(resource)
            : classLoader.getResourceAsStream(resource);

        if (inputStream == null) {
            throw new CompileFlowException("Failed to load flow, resource is " + resource);
        }
        return inputStream;
    }

}
