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
package com.alibaba.compileflow.engine.process.preruntime.compiler.impl.support;

import com.alibaba.compileflow.engine.process.preruntime.compiler.CompileOption;
import com.alibaba.compileflow.engine.process.preruntime.compiler.JavaCompiler;
import com.alibaba.compileflow.engine.process.preruntime.compiler.JavaSource;
import com.alibaba.compileflow.engine.process.preruntime.compiler.impl.CompileConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author yusu
 */
public class JdkJavaCompiler implements JavaCompiler {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdkJavaCompiler.class);

    @Override
    public void compile(JavaSource javaSource, File outputFile, CompileOption compileOption) throws Exception {
        javax.tools.JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        List<StringJavaFileObject> javaFileObjects = new ArrayList<>();
        StringJavaFileObject srcObject = new StringJavaFileObject(javaSource.getTargetFullClassName(),
            javaSource.getJavaSourceCode());
        javaFileObjects.add(srcObject);

        Iterable<String> options = Arrays.asList("-d", outputFile.getPath());

        javax.tools.JavaCompiler.CompilationTask task = compiler.getTask(
            CompileLogHelper.getInstance().getLogPrintWriter(), fileManager, null,
            options, null, javaFileObjects);
        boolean result = task.call();

        if (!result) {
            LOGGER.error("Compile class error, class name is " + javaSource.getTargetFullClassName());
        }
    }

    private static class StringJavaFileObject extends SimpleJavaFileObject {

        private final String code;

        public StringJavaFileObject(String name, String code) {
            super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension),
                Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            return code;
        }

    }

    private static class CompileLogHelper {

        private static final Logger LOGGER = LoggerFactory.getLogger(CompileLogHelper.class);

        private static final String COMPILE_LOG = CompileConstants.FLOW_COMPILE_CLASS_DIR + "compile.log";

        private static volatile CompileLogHelper instance;

        private static PrintWriter logPrintWriter;

        public static CompileLogHelper getInstance() {
            if (instance == null) {
                synchronized (CompileLogHelper.class) {
                    if (instance == null) {
                        init();
                        instance = new CompileLogHelper();
                    }
                }
            }
            return instance;
        }

        private static void init() {
            File logfile = new File(COMPILE_LOG);
            if (!logfile.exists()) {
                try {
                    if (!logfile.createNewFile()) {
                        LOGGER.error("Create java compile log error");
                    }
                } catch (IOException e) {
                    LOGGER.error("Create java compile log error", e);
                }
            }
            try {
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new FileOutputStream(logfile), "UTF-8");
                logPrintWriter = new PrintWriter(outputStreamWriter, true);
            } catch (Exception e) {
                LOGGER.error("Create java compile log printWriter error", e);
            }
        }

        public PrintWriter getLogPrintWriter() {
            return logPrintWriter;
        }

    }

}
