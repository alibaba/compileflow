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

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * @author wuxiang
 * @author yusu
 */
public class StringFlowStreamSource implements FlowStreamSource {

    private String flowString;

    public static StringFlowStreamSource of(String flowString) {
        StringFlowStreamSource stringFlowStreamSource = new StringFlowStreamSource();
        stringFlowStreamSource.setFlowString(flowString);
        return stringFlowStreamSource;
    }

    private void setFlowString(String flowString) {
        this.flowString = flowString;
    }

    @Override
    public InputStream getFlow() {
        return new ByteArrayInputStream(flowString.getBytes());
    }

}
