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
package com.alibaba.compileflow.engine.process.builder.compiler.impl.support;

import com.alibaba.compileflow.engine.extension.annotation.ExtensionRealization;
import com.alibaba.compileflow.engine.process.builder.compiler.CompileOption;
import com.alibaba.compileflow.engine.process.builder.compiler.JavaCompiler;
import com.alibaba.compileflow.engine.process.builder.compiler.JavaSource;
import com.alibaba.compileflow.engine.process.builder.compiler.impl.CompileConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author yusu
 */
@ExtensionRealization()
public class JdkJavaCompiler implements JavaCompiler {

    @Override
    public void compile(JavaSource javaSource, File outputFile, CompileOption compileOption) throws Exception {
        CompileLogHelper logHelper = CompileLogHelper.getInstance();
        String className = javaSource.getTargetFullClassName();
        long startTime = System.currentTimeMillis();

        try {
            // 记录编译开始
            logHelper.logCompileStart(className);
            logHelper.logConcurrentCompile(className, Thread.currentThread().getName());

            javax.tools.JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                String errorMsg = "No Java compiler available. Please ensure JDK is properly installed.";
                logHelper.logCompileError(className, errorMsg);
                throw new RuntimeException(errorMsg);
            }

            StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
            List<StringJavaFileObject> javaFileObjects = new ArrayList<>();
            StringJavaFileObject srcObject = new StringJavaFileObject(className,
                    javaSource.getJavaSourceCode());
            javaFileObjects.add(srcObject);

            Iterable<String> options = Arrays.asList("-d", outputFile.getPath());

            javax.tools.JavaCompiler.CompilationTask task = compiler.getTask(
                    logHelper.getLogPrintWriter(), fileManager, null,
                    options, null, javaFileObjects);

            logHelper.logCompileInfo(className, "Compilation task created, starting compilation...");
            boolean result = task.call();

            long endTime = System.currentTimeMillis();

            if (result) {
                logHelper.logCompileSuccess(className);
                logHelper.logCompileStats(className, startTime, endTime);
            } else {
                String errorMsg = "Compilation task returned false";
                logHelper.logCompileError(className, errorMsg);
                logHelper.logCompileStats(className, startTime, endTime);
                throw new RuntimeException("Compilation failed for class: " + className);
            }

        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            logHelper.logCompileError(className, e.getMessage());
            logHelper.logCompileStats(className, startTime, endTime);
            throw e;
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

        private static final CompileLogHelper INSTANCE;
        private static final PrintWriter LOG_PRINT_WRITER;

        static {
            try {
                File logDir = new File(CompileConstants.FLOW_COMPILE_CLASS_DIR);
                if (!logDir.exists() && !logDir.mkdirs()) {
                    throw new RuntimeException("Failed to create compile log directory: " + logDir.getAbsolutePath());
                }

                File logfile = new File(COMPILE_LOG);
                if (!logfile.exists() && !logfile.createNewFile()) {
                    throw new RuntimeException("Failed to create compile log file: " + logfile.getAbsolutePath());
                }

                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(
                    new FileOutputStream(logfile, true), StandardCharsets.UTF_8);
                LOG_PRINT_WRITER = new PrintWriter(outputStreamWriter, true);

                INSTANCE = new CompileLogHelper();

                LOG_PRINT_WRITER.println("[" + new java.util.Date() + "] CompileLogHelper initialized successfully");
                LOG_PRINT_WRITER.flush();
            } catch (Exception e) {
                LOGGER.error("Failed to initialize CompileLogHelper", e);
                throw new RuntimeException("Failed to initialize CompileLogHelper", e);
            }
        }

        private CompileLogHelper() {
        }

        public static CompileLogHelper getInstance() {
            return INSTANCE;
        }

        public PrintWriter getLogPrintWriter() {
            return LOG_PRINT_WRITER;
        }

        /**
         * 记录编译开始日志
         */
        public void logCompileStart(String className) {
            if (LOG_PRINT_WRITER != null) {
                LOG_PRINT_WRITER.println("[" + new java.util.Date() + "] Starting compilation for class: " + className);
                LOG_PRINT_WRITER.flush();
            }
        }

        /**
         * 记录编译成功日志
         */
        public void logCompileSuccess(String className) {
            if (LOG_PRINT_WRITER != null) {
                LOG_PRINT_WRITER.println("[" + new java.util.Date() + "] Compilation successful for class: " + className);
                LOG_PRINT_WRITER.flush();
            }
        }

        /**
         * 记录编译失败日志
         */
        public void logCompileError(String className, String errorMessage) {
            if (LOG_PRINT_WRITER != null) {
                LOG_PRINT_WRITER.println("[" + new java.util.Date() + "] Compilation failed for class: " + className + " - Error: " + errorMessage);
                LOG_PRINT_WRITER.flush();
            }
            LOGGER.error("Compilation failed for class: {} - Error: {}", className, errorMessage);
        }

        /**
         * 记录编译警告日志
         */
        public void logCompileWarning(String className, String warningMessage) {
            if (LOG_PRINT_WRITER != null) {
                LOG_PRINT_WRITER.println("[" + new java.util.Date() + "] Compilation warning for class: " + className + " - Warning: " + warningMessage);
                LOG_PRINT_WRITER.flush();
            }
            LOGGER.warn("Compilation warning for class: {} - Warning: {}", className, warningMessage);
        }

        /**
         * 记录编译信息日志
         */
        public void logCompileInfo(String className, String infoMessage) {
            if (LOG_PRINT_WRITER != null) {
                LOG_PRINT_WRITER.println("[" + new java.util.Date() + "] Compilation info for class: " + className + " - Info: " + infoMessage);
                LOG_PRINT_WRITER.flush();
            }
            LOGGER.info("Compilation info for class: {} - Info: {}", className, infoMessage);
        }

        /**
         * 记录并发编译信息
         */
        public void logConcurrentCompile(String className, String threadName) {
            if (LOG_PRINT_WRITER != null) {
                LOG_PRINT_WRITER.println("[" + new java.util.Date() + "] Concurrent compilation for class: " + className + " by thread: " + threadName);
                LOG_PRINT_WRITER.flush();
            }
        }

        /**
         * 关闭日志输出流
         */
        public void close() {
            if (LOG_PRINT_WRITER != null) {
                try {
                    LOG_PRINT_WRITER.println("[" + new java.util.Date() + "] CompileLogHelper shutting down");
                    LOG_PRINT_WRITER.flush();
                    LOG_PRINT_WRITER.close();
                } catch (Exception e) {
                    LOGGER.error("Error closing CompileLogHelper", e);
                }
            }
        }

        public boolean isAvailable() {
            return LOG_PRINT_WRITER != null && !LOG_PRINT_WRITER.checkError();
        }

        public String getLogFilePath() {
            return COMPILE_LOG;
        }

        public void flush() {
            if (LOG_PRINT_WRITER != null) {
                LOG_PRINT_WRITER.flush();
            }
        }

        /**
         * 记录编译统计信息
         */
        public void logCompileStats(String className, long startTime, long endTime) {
            if (LOG_PRINT_WRITER != null) {
                long duration = endTime - startTime;
                LOG_PRINT_WRITER.println("[" + new java.util.Date() + "] Compilation stats for class: " + className + " - Duration: " + duration + "ms");
                LOG_PRINT_WRITER.flush();
            }
        }
    }

}
