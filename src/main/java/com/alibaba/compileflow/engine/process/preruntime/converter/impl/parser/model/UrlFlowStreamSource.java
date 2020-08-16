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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * @author wuxiang
 * @author yusu
 */
public class UrlFlowStreamSource implements FlowStreamSource {

    private URL url;

    public static UrlFlowStreamSource of(URL url) {
        UrlFlowStreamSource urlFlowStreamSource = new UrlFlowStreamSource();
        urlFlowStreamSource.setUrl(url);
        return urlFlowStreamSource;
    }

    private void setUrl(URL url) {
        this.url = url;
    }

    @Override
    public InputStream getFlow() {
        try {
            return new BufferedInputStream(url.openStream());
        } catch (IOException e) {
            throw new CompileFlowException("Failed to open stream, url is " + url, e);
        }
    }

}
