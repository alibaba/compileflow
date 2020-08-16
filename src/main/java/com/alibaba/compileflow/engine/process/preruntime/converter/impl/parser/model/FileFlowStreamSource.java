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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * @author wuxiang
 * @author yusu
 */
public class FileFlowStreamSource implements FlowStreamSource {

    private File file;

    public static FileFlowStreamSource of(File file) {
        FileFlowStreamSource fileFlowStreamSource = new FileFlowStreamSource();
        fileFlowStreamSource.setFile(file);
        return fileFlowStreamSource;
    }

    private void setFile(File file) {
        this.file = file;
    }

    @Override
    public InputStream getFlow() {
        try {
            return new FileInputStream(file);
        } catch (FileNotFoundException e) {
            throw new CompileFlowException("Failed to get flow, file is " + file, e);
        }
    }

}
