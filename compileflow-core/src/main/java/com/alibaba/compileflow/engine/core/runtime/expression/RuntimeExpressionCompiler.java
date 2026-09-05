/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.core.runtime.expression;

import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig;
import com.alibaba.compileflow.engine.core.java.compiler.GeneratedClassCompiler;
import com.alibaba.compileflow.engine.core.java.compiler.JavaCompiler;
import com.alibaba.compileflow.engine.core.runtime.execution.ConditionSemantics;
import com.alibaba.compileflow.engine.core.type.DataTypes;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Compiles only the typed Java expressions needed by an interpreted Process runtime.
 *
 * @author yusu
 */
public final class RuntimeExpressionCompiler {
    public static final String GENERATOR_VERSION = "compileflow.runtime-expressions/v2";
    private static final String GENERATED_PACKAGE = "com.alibaba.compileflow.generated.expression";
    private final GeneratedClassCompiler classCompiler;

    public RuntimeExpressionCompiler(JavaCompiler javaCompiler, JavaDiagnosticsConfig compilationConfig) {
        this.classCompiler = new GeneratedClassCompiler(Objects.requireNonNull(compilationConfig, "compilationConfig"),
                Objects.requireNonNull(javaCompiler, "javaCompiler"));
    }

    public CompiledExpressionEvaluator compile(String processCode, List<RuntimeExpression> expressions,
            ClassLoader classLoader) {
        String code = ProcessIdentifiers.requireCode(processCode);
        ClassLoader loader = Objects.requireNonNull(classLoader, "classLoader");
        List<RuntimeExpression> unique =
                new ArrayList<>(new LinkedHashSet<>(Objects.requireNonNull(expressions, "expressions")));
        if (unique.isEmpty()) {
            return new CompiledExpressionEvaluator(Map.of(), null);
        }

        String digest = digest(unique);
        String simpleName = "ProcessExpressions_" + digest.substring(0, 16);
        String className = GENERATED_PACKAGE + "." + simpleName;
        Class<? extends RuntimeExpressionProgram> type = classCompiler
            .compile(className, source(simpleName, unique, loader), loader,
                    Map.of("generator-version", GENERATOR_VERSION, "process-code", code, "expression-digest", digest))
            .asSubclass(RuntimeExpressionProgram.class);
        RuntimeExpressionProgram program = instantiate(type);
        Map<RuntimeExpression, Integer> indexes = new LinkedHashMap<>();
        for (int index = 0; index < unique.size(); index++) {
            indexes.put(unique.get(index), index);
        }
        return new CompiledExpressionEvaluator(indexes, program);
    }

    private static RuntimeExpressionProgram instantiate(Class<? extends RuntimeExpressionProgram> type) {
        try {
            return type.getConstructor().newInstance();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Generated Process expression program could not be instantiated", failure);
        }
    }

    private static String source(String simpleName, List<RuntimeExpression> expressions, ClassLoader classLoader) {
        StringBuilder source = new StringBuilder(4_096 + expressions.size() * 512);
        source.append("package ").append(GENERATED_PACKAGE).append(";\n\n");
        if (expressions
            .stream()
            .anyMatch(expression -> expression.kind() == RuntimeExpression.Kind.CONDITION)) {
            source.append("import ").append(ConditionSemantics.class.getName()).append(";\n");
        }
        source
            .append("import ")
            .append(RuntimeExpressionProgram.class.getName())
            .append(";\n")
            .append("import javax.annotation.processing.Generated;\n\n")
            .append("@Generated(value = \"")
            .append(GENERATOR_VERSION)
            .append("\")\n")
            .append("public final class ")
            .append(simpleName)
            .append(" implements RuntimeExpressionProgram {\n")
            .append("    @Override\n")
            .append("    public Object evaluate(int expressionIndex, Object[] arguments) {\n")
            .append("        return switch (expressionIndex) {\n");
        for (int index = 0; index < expressions.size(); index++) {
            source.append("            case ").append(index).append(" -> expression").append(index).append(
                    "(arguments);\n");
        }
        source
            .append(
                    "            default -> throw new IllegalArgumentException(\"Unknown expression index: \" + expressionIndex);\n")
            .append("        };\n")
            .append("    }\n\n");
        for (int index = 0; index < expressions.size(); index++) {
            appendExpression(source, index, expressions.get(index), classLoader);
        }
        removeTrailingBlankLines(source);
        return source.append("}\n").toString();
    }

    private static void removeTrailingBlankLines(StringBuilder source) {
        while (source.length() >= 2 && source.charAt(source.length() - 1) == '\n'
                && source.charAt(source.length() - 2) == '\n') {
            source.setLength(source.length() - 1);
        }
    }

    private static void appendExpression(StringBuilder source, int index, RuntimeExpression expression,
            ClassLoader classLoader) {
        source.append("    private static Object expression").append(index).append("(Object[] arguments) {\n");
        for (int argument = 0; argument < expression.bindings().size(); argument++) {
            RuntimeExpression.Binding binding = expression.bindings().get(argument);
            Class<?> type = loadType(binding.typeName(), classLoader);
            String declaredType = sourceType(type);
            String castType = sourceType(type.isPrimitive() ? DataTypes.getWrapperClass(type) : type);
            source
                .append("        ")
                .append(declaredType)
                .append(' ')
                .append(binding.name())
                .append(" = (")
                .append(castType)
                .append(") arguments[")
                .append(argument)
                .append("];\n");
        }
        source.append("        return ");
        if (expression.kind() == RuntimeExpression.Kind.CONDITION) {
            source.append("ConditionSemantics.isTrue(");
        }
        source.append(expression.source());
        if (expression.kind() == RuntimeExpression.Kind.CONDITION) {
            source.append(')');
        }
        source.append(";\n    }\n\n");
    }

    private static Class<?> loadType(String typeName, ClassLoader classLoader) {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(classLoader);
            return DataTypes.getJavaClass(typeName);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private static String sourceType(Class<?> type) {
        if ("java.lang".equals(type.getPackageName())) {
            return type.getSimpleName();
        }
        String canonicalName = type.getCanonicalName();
        return canonicalName == null ? type.getName() : canonicalName;
    }

    private static String digest(List<RuntimeExpression> expressions) {
        StringBuilder canonical = new StringBuilder();
        for (RuntimeExpression expression : expressions) {
            canonical.append(expression.kind()).append('\u0000').append(expression.source()).append('\u0000');
            expression
                .bindings()
                .forEach(binding -> canonical
                    .append(binding.name())
                    .append('\u0000')
                    .append(binding.typeName())
                    .append('\u0000'));
        }
        try {
            return HexFormat
                .of()
                .formatHex(MessageDigest
                    .getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
