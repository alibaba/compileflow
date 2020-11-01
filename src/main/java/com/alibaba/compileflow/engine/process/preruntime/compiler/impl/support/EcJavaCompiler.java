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

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.process.preruntime.compiler.CompileOption;
import com.alibaba.compileflow.engine.process.preruntime.compiler.JavaCompiler;
import com.alibaba.compileflow.engine.process.preruntime.compiler.JavaSource;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.Compiler;
import org.eclipse.jdt.internal.compiler.*;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.env.INameEnvironment;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author yusu
 */
public class EcJavaCompiler implements JavaCompiler {

    private static final Logger LOGGER = LoggerFactory.getLogger(EcJavaCompiler.class);

    /**
     * java源码版本
     */
    private static final String DEFAULT_JAVA_SOURCE_VERSION = System.getProperty("java.specification.version");
    /**
     * 目标class运行的版本
     */
    private static final String DEFAULT_JAVA_TARGET_VERSION = System.getProperty("java.specification.version");

    /**
     * java源码版本
     */
    private static final String JAVA_SOURCE_VERSION = DEFAULT_JAVA_SOURCE_VERSION;

    /**
     * 目标class运行的版本
     */
    private static final String JAVA_TARGET_VERSION = DEFAULT_JAVA_TARGET_VERSION;

    /**
     * 允许的java规范版本
     */
    private static final Set<String> ALLOWED_SPEC_VERSION = new HashSet<>(Arrays.asList(
        "1.7",
        "1.8"
    ));

    @Override
    public void compile(JavaSource javaSource, File outputFile, CompileOption compileOption) throws Exception {
        ClassLoader classLoader = this.getClass().getClassLoader();

        File javaSourceFile = javaSource.getJavaSourceFile();
        String targetClassName = javaSource.getTargetFullClassName();
        String[] fileNames = new String[] {javaSourceFile.getAbsolutePath()};
        String[] classNames = new String[] {targetClassName};

        List<IProblem> problems = new ArrayList<>();
        INameEnvironment env = new INameEnvironment() {
            @Override
            public NameEnvironmentAnswer findType(char[][] compoundTypeName) {
                return findType(Arrays.stream(compoundTypeName).map(String::new)
                    .collect(Collectors.joining(".")));
            }

            @Override
            public NameEnvironmentAnswer findType(char[] typeName, char[][] packageName) {
                return findType(Arrays.stream(packageName).map(String::new).collect(Collectors.joining("."))
                    .concat("." + new String(typeName)));
            }

            private NameEnvironmentAnswer findType(String className) {
                InputStream is = null;
                try {
                    if (className.equals(targetClassName)) {
                        ICompilationUnit compilationUnit = new CompilationUnit(javaSourceFile.getAbsolutePath(),
                            className, compileOption.getEncoding());
                        return new NameEnvironmentAnswer(compilationUnit, null);
                    }
                    String resourceName = className.replace('.', '/') + ".class";
                    is = classLoader.getResourceAsStream(resourceName);
                    if (is != null) {
                        byte[] classBytes;
                        byte[] buf = new byte[8192];
                        ByteArrayOutputStream baos = new ByteArrayOutputStream(buf.length);
                        int count;
                        while ((count = is.read(buf, 0, buf.length)) > 0) {
                            baos.write(buf, 0, count);
                        }
                        baos.flush();
                        classBytes = baos.toByteArray();
                        char[] fileName = className.toCharArray();
                        ClassFileReader classFileReader = new ClassFileReader(classBytes, fileName, true);
                        return new NameEnvironmentAnswer(classFileReader, null);
                    }
                } catch (Exception e) {
                    LOGGER.error(e.getMessage(), e);
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (IOException exc) {
                            LOGGER.error("InputStream close error", exc);
                        }
                    }
                }
                return null;
            }

            private boolean isPackage(String result) {
                if (result.equals(targetClassName)) {
                    return false;
                }
                String resourceName = result.replace('.', '/') + ".class";
                InputStream is = classLoader.getResourceAsStream(resourceName);
                return is == null;
            }

            @Override
            public boolean isPackage(char[][] parentPackageName, char[] packageName) {
                String result = parentPackageName != null ? Arrays.stream(parentPackageName).map(String::new)
                    .collect(Collectors.joining(".")) : "";
                String pName = new String(packageName);
                if (Character.isUpperCase(pName.charAt(0))) {
                    if (!isPackage(result)) {
                        return false;
                    }
                }
                return isPackage(result.concat("." + pName));
            }

            @Override
            public void cleanup() {
            }
        };

        IErrorHandlingPolicy policy = DefaultErrorHandlingPolicies.proceedWithAllProblems();

        Map<String, String> settings = new HashMap<>();
        settings.put(CompilerOptions.OPTION_LineNumberAttribute, CompilerOptions.GENERATE);
        settings.put(CompilerOptions.OPTION_SourceFileAttribute, CompilerOptions.GENERATE);
        settings.put(CompilerOptions.OPTION_ReportDeprecation, CompilerOptions.IGNORE);
        settings.put(CompilerOptions.OPTION_LocalVariableAttribute, CompilerOptions.GENERATE);
        settings.put(CompilerOptions.OPTION_Compliance, CompilerOptions.VERSION_1_8);
        settings.put(CompilerOptions.OPTION_Source, CompilerOptions.VERSION_1_8);

        if (ALLOWED_SPEC_VERSION.contains(JAVA_TARGET_VERSION)) {
            settings.put(CompilerOptions.OPTION_TargetPlatform, JAVA_TARGET_VERSION);
        } else {
            settings.put(CompilerOptions.OPTION_TargetPlatform, DEFAULT_JAVA_TARGET_VERSION);
        }

        IProblemFactory problemFactory = new DefaultProblemFactory(Locale.getDefault());

        ICompilerRequestor requestor = result -> {
            try {
                if (result.hasProblems()) {
                    for (IProblem problem : result.getProblems()) {
                        if (problem.isError()) {
                            try {
                                problems.add(problem);
                            } catch (Exception e) {
                                LOGGER.error(e.getMessage(), e);
                            }
                        }
                    }
                }
                if (problems.isEmpty()) {
                    ClassFile[] classFiles = result.getClassFiles();
                    for (ClassFile classFile : classFiles) {
                        char[][] compoundName = classFile.getCompoundName();
                        String className = Arrays.stream(compoundName).map(String::new).collect(
                            Collectors.joining("."));
                        byte[] bytes = classFile.getBytes();

                        String outFile = outputFile + "/" + className.replace('.', '/') + ".class";

                        File packagePath = new File(outFile.substring(0, outFile.lastIndexOf("/")));
                        if (!packagePath.exists()) {
                            if (!packagePath.mkdirs()) {
                                throw new RuntimeException("Failed to mkdir, package path is " + packagePath);
                            }
                        }

                        FileOutputStream outputStream = new FileOutputStream(outFile);
                        BufferedOutputStream bos = new BufferedOutputStream(outputStream);
                        bos.write(bytes);
                        bos.close();
                    }
                }
            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        };

        ICompilationUnit[] compilationUnits = new ICompilationUnit[classNames.length];
        for (int i = 0; i < compilationUnits.length; i++) {
            String className = classNames[i];
            compilationUnits[i] = new CompilationUnit(fileNames[i], className, compileOption.getEncoding());
        }
        CompilerOptions cOptions = new CompilerOptions(settings);
        cOptions.parseLiteralExpressionsAsConstants = true;

        Compiler compiler = new Compiler(env, policy, cOptions, requestor, problemFactory);

        compiler.compile(compilationUnits);

        if (!problems.isEmpty()) {
            throw new CompileFlowException(getErrorMsg(javaSourceFile, targetClassName, problems));
        }
    }

    private String getErrorMsg(File javaFile, String className, Collection<IProblem> errors) {
        StringBuilder sb = new StringBuilder();

        sb.append("compile file[").append(javaFile.getAbsoluteFile()).append("] to class[").append(className)
            .append("] failed,");

        for (IProblem problem : errors) {
            sb.append(problem).append(System.getProperty("line.separator"));
        }

        return sb.toString();
    }

    static class CompilationUnit implements ICompilationUnit {
        private final String className;
        private final String sourceFile;
        private final String encode;

        CompilationUnit(String sourceFile, String className, String encode) {
            this.className = className;
            this.sourceFile = sourceFile;
            this.encode = encode;
        }

        @Override
        public char[] getFileName() {
            return sourceFile.toCharArray();
        }

        @Override
        public char[] getContents() {
            char[] result = null;
            try (FileInputStream is = new FileInputStream(sourceFile)) {
                Reader reader = new BufferedReader(new InputStreamReader(is, encode));
                char[] chars = new char[8192];
                StringBuilder buf = new StringBuilder();
                int count;
                while ((count = reader.read(chars, 0, chars.length)) > 0) {
                    buf.append(chars, 0, count);
                }
                result = new char[buf.length()];
                buf.getChars(0, result.length, result, 0);
                reader.close();
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
            return result;
        }

        @Override
        public char[] getMainTypeName() {
            int dot = className.lastIndexOf('.');
            if (dot > 0) {
                return className.substring(dot + 1).toCharArray();
            }
            return className.toCharArray();
        }

        @Override
        public char[][] getPackageName() {
            StringTokenizer stringTokenizer = new StringTokenizer(className, ".");
            char[][] result = new char[stringTokenizer.countTokens() - 1][];
            for (int i = 0; i < result.length; i++) {
                String token = stringTokenizer.nextToken();
                result[i] = token.toCharArray();
            }
            return result;
        }

        @Override
        public boolean ignoreOptionalProblems() {
            return false;
        }
    }

}
