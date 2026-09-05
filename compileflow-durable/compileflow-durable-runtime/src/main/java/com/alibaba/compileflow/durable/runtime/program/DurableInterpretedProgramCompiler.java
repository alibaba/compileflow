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
package com.alibaba.compileflow.durable.runtime.program;

import com.alibaba.compileflow.durable.runtime.machine.BoundExpression;
import com.alibaba.compileflow.durable.runtime.machine.DurableMachinePlan;
import com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig;
import com.alibaba.compileflow.engine.core.java.compiler.GeneratedClassCompiler;
import com.alibaba.compileflow.engine.core.type.DataTypes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Compiles the direct Durable Machine Interpreter and its small typed expression program.
 *
 * @author yusu
 */
public final class DurableInterpretedProgramCompiler implements DurableProgramCompiler {
    public static final String GENERATOR_VERSION = "compileflow.durable-expressions/v1";
    private static final String GENERATED_PACKAGE = "com.alibaba.compileflow.generated.durable.expression";
    private static final Map<String, String> PRIMITIVE_WRAPPERS = Map.of("boolean", "Boolean", "byte", "Byte", "short",
            "Short", "int", "Integer", "long", "Long", "float", "Float", "double", "Double", "char", "Character");
    private final GeneratedClassCompiler classCompiler;

    public DurableInterpretedProgramCompiler() {
        this(JavaDiagnosticsConfig.defaults());
    }

    public DurableInterpretedProgramCompiler(JavaDiagnosticsConfig compilationConfig) {
        this.classCompiler = new GeneratedClassCompiler(Objects.requireNonNull(compilationConfig, "compilationConfig"));
    }

    @Override
    public DurableProgram compile(DurableMachinePlan machinePlan, ClassLoader classLoader) {
        DurableMachinePlan plan = Objects.requireNonNull(machinePlan, "machinePlan");
        ClassLoader loader = Objects.requireNonNull(classLoader, "classLoader");
        List<BoundExpression> expressions = expressions(plan);
        if (expressions.isEmpty()) {
            return new DurableMachineInterpreter(plan, (expression, state, frames) -> {
                throw new IllegalArgumentException("Durable Machine declares no expression: " + expression.source());
            }, loader);
        }
        String simpleName = "DurableExpressions_" + plan.digest().substring(0, 16);
        String className = GENERATED_PACKAGE + "." + simpleName;
        String source = source(simpleName, expressions, loader);
        Class<? extends DurableExpressionProgram> expressionClass = classCompiler
            .compile(className, source, loader,
                    Map.of("generator-version", GENERATOR_VERSION, "process-code", plan
                                .semanticPlan()
                                .getProcessCode(), "machine-digest", plan.digest()))
            .asSubclass(DurableExpressionProgram.class);
        DurableExpressionProgram program = instantiate(expressionClass);
        Map<BoundExpression, Integer> indexes = new LinkedHashMap<>();
        for (int index = 0; index < expressions.size(); index++) {
            indexes.put(expressions.get(index), index);
        }
        return new DurableMachineInterpreter(plan, (expression, state, frames) -> {
            Integer index = indexes.get(expression);
            if (index == null) {
                throw new IllegalArgumentException("Expression does not belong to this Durable Machine");
            }
            Object[] arguments = new Object[expression.bindings().size()];
            for (int argument = 0; argument < arguments.length; argument++) {
                BoundExpression.Binding binding = expression.bindings().get(argument);
                arguments[argument] = binding.source() == BoundExpression.Binding.Source.STATE
                        ? state.get(binding.name())
                        : frames.get(binding.name());
            }
            return program.evaluate(index, arguments);
        }, loader);
    }

    private static DurableExpressionProgram instantiate(Class<? extends DurableExpressionProgram> type) {
        try {
            return type.getConstructor().newInstance();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Generated Durable expression program could not be instantiated", failure);
        }
    }

    private static List<BoundExpression> expressions(DurableMachinePlan plan) {
        Map<BoundExpression, Boolean> result = new LinkedHashMap<>();
        new TreeMap<>(plan.steps())
            .values()
            .forEach(step -> addStepExpressions(result, step));
        new TreeMap<>(plan.iterations())
            .values()
            .forEach(iteration -> {
                if (iteration instanceof DurableMachinePlan.Iteration.While loop) {
                    add(result, loop.condition());
                }
                DurableMachinePlan.Body body = iterationBody(iteration);
                if (body instanceof DurableMachinePlan.Body.Operation operation) {
                    add(result, operation.scheduleExpression());
                }
            });
        return List.copyOf(result.keySet());
    }

    private static void addStepExpressions(Map<BoundExpression, Boolean> result, DurableMachinePlan.Step step) {
        if (step instanceof DurableMachinePlan.Step.ChooseOne choose) {
            choose
                .branches()
                .forEach(branch -> add(result, branch.condition()));
        } else if (step instanceof DurableMachinePlan.Step.ForkSelected fork) {
            fork
                .branches()
                .forEach(branch -> add(result, branch.condition()));
        } else if (step instanceof DurableMachinePlan.Step.Timer timer) {
            add(result, timer.scheduleExpression());
        } else if (step instanceof DurableMachinePlan.Step.BreakIteration control) {
            add(result, control.condition());
        } else if (step instanceof DurableMachinePlan.Step.ContinueIteration control) {
            add(result, control.condition());
        }
    }

    private static DurableMachinePlan.Body iterationBody(DurableMachinePlan.Iteration iteration) {
        return iteration instanceof DurableMachinePlan.Iteration.While loop
                ? loop.body()
                : ((DurableMachinePlan.Iteration.ForEach) iteration).body();
    }

    private static void add(Map<BoundExpression, Boolean> expressions, BoundExpression expression) {
        if (expression != null) {
            expressions.putIfAbsent(expression, Boolean.TRUE);
        }
    }

    private static String source(String simpleName, List<BoundExpression> expressions, ClassLoader classLoader) {
        StringBuilder source = new StringBuilder(4_096 + expressions.size() * 512);
        source
            .append("package ")
            .append(GENERATED_PACKAGE)
            .append(";\n\n")
            .append("import com.alibaba.compileflow.durable.runtime.program.DurableExpressionProgram;\n")
            .append("import javax.annotation.processing.Generated;\n\n")
            .append("@Generated(value = \"")
            .append(GENERATOR_VERSION)
            .append("\")\n")
            .append("public final class ")
            .append(simpleName)
            .append(" implements DurableExpressionProgram {\n")
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

    private static void appendExpression(StringBuilder source, int index, BoundExpression expression,
            ClassLoader classLoader) {
        source.append("    private static Object expression").append(index).append("(Object[] arguments) {\n");
        for (int argument = 0; argument < expression.bindings().size(); argument++) {
            BoundExpression.Binding binding = expression.bindings().get(argument);
            String declaredType = sourceType(binding.typeName(), classLoader);
            String castType = PRIMITIVE_WRAPPERS.getOrDefault(declaredType, declaredType);
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
        source.append("        return ").append(expression.source()).append(";\n").append("    }\n\n");
    }

    private static String sourceType(String typeName, ClassLoader classLoader) {
        String rawType = DataTypes.getRawTypeName(typeName);
        if (PRIMITIVE_WRAPPERS.containsKey(rawType) || rawType.endsWith("[]")) {
            return rawType;
        }
        try {
            String canonicalName = Class.forName(rawType, false, classLoader).getCanonicalName();
            if (canonicalName != null && canonicalName.startsWith("java.lang.")) {
                return canonicalName.substring("java.lang.".length());
            }
            return canonicalName == null ? rawType : canonicalName;
        } catch (ClassNotFoundException ignored) {
            return rawType;
        }
    }
}
