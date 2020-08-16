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
package com.alibaba.compileflow.engine.process.preruntime.compiler;

import java.io.File;

/**
 * @author yusu
 */
public class JavaSource {

    private File javaSourceFile;

    private String javaSourceCode;

    private String targetFullClassName;

    public static JavaSource of(File javaSourceFile, String javaSourceCode, String targetFullClassName) {
        JavaSource javaSource = new JavaSource();
        javaSource.javaSourceFile = javaSourceFile;
        javaSource.javaSourceCode = javaSourceCode;
        javaSource.targetFullClassName = targetFullClassName;
        return javaSource;
    }

    public File getJavaSourceFile() {
        return javaSourceFile;
    }

    public String getJavaSourceCode() {
        return javaSourceCode;
    }

    public String getTargetFullClassName() {
        return targetFullClassName;
    }

}
