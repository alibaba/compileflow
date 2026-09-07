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
package com.alibaba.compileflow.engine.core.runtime.action;

import com.alibaba.compileflow.engine.core.semantic.plan.ActionInvocation;
import com.alibaba.compileflow.engine.core.semantic.plan.ActionPlan;
import com.alibaba.compileflow.engine.core.semantic.plan.ReconcilePlan;
import com.alibaba.compileflow.engine.core.runtime.script.ScriptExecutorRegistry;
import com.alibaba.compileflow.engine.core.type.DataTypes;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import com.alibaba.compileflow.engine.spi.script.ScriptProgram;
import com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves and invokes immutable semantic Action descriptions in the current deployment.
 *
 * @author yusu
 */
public final class ProcessActionInvoker {
    private final ProcessComponentResolver components;
    private final ScriptExecutorRegistry scripts;
    private final ClassLoader classLoader;
    private final Map<ScriptProgramSpec, ScriptProgram> scriptPrograms;

    public ProcessActionInvoker(ProcessComponentResolver components, ScriptExecutorRegistry scripts,
            ClassLoader classLoader) {
        this(components, scripts, classLoader, Map.of());
    }

    public ProcessActionInvoker(ProcessComponentResolver components, ScriptExecutorRegistry scripts,
            ClassLoader classLoader, Map<ScriptProgramSpec, ScriptProgram> scriptPrograms) {
        this.components = Objects.requireNonNull(components, "components");
        this.scripts = Objects.requireNonNull(scripts, "scripts");
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
        this.scriptPrograms = Map.copyOf(Objects.requireNonNull(scriptPrograms, "scriptPrograms"));
    }

    /**
     * Checks resolution metadata without constructing or resolving an application target.
     */
    public boolean isStructurallyReady(ActionPlan action) {
        ActionPlan plan = Objects.requireNonNull(action, "action");
        return isStructurallyReady(plan.invocation(),
                plan.inputs().stream().map(ActionPlan.Input::declaredType).toList(),
                plan.output() == null ? null : plan.output().resultType());
    }

    /**
     * Checks reconcile adapter metadata without entering application code.
     */
    public boolean isStructurallyReady(ReconcilePlan reconcile) {
        ReconcilePlan plan = Objects.requireNonNull(reconcile, "reconcile");
        return isStructurallyReady(plan.invocation(),
                plan.inputs().stream().map(ReconcilePlan.Input::declaredType).toList(), null);
    }

    private boolean isStructurallyReady(ActionInvocation invocation, List<String> declaredTypes,
            String declaredResultType) {
        try {
            if (invocation instanceof ActionInvocation.Script script) {
                return scripts.getLanguageNames().contains(script.language());
            }
            Class<?> declaredType = load(declaredClass(invocation));
            Method method = requireMethod(declaredType, invocation, declaredTypes);
            if (declaredResultType != null
                    && !DataTypes.isJavaAssignmentCompatible(method.getReturnType(),
                            DataTypes.getJavaClass(declaredResultType, classLoader))) {
                return false;
            }
            return !(invocation instanceof ActionInvocation.Java)
                    || declaredType.getDeclaredConstructor().canAccess(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    /**
     * Invokes an Action from values already resolved from Process state and lexical bindings.
     */
    public Map<String, Object> invoke(ActionPlan action, Map<String, Object> input, String effectId) throws Exception {
        Object result = invokeRaw(action, input, effectId);
        return mapResult(action, result);
    }

    /**
     * Invokes an Action while preserving its raw declared return value.
     */
    public Object invokeRaw(ActionPlan action, Map<String, Object> input, String effectId) throws Exception {
        ActionPlan plan = Objects.requireNonNull(action, "action");
        Map<String, Object> arguments = Objects.requireNonNull(input, "input");
        return invokeRaw(plan.invocation(), plan.inputs().stream().map(ActionPlan.Input::declaredType).toList(),
                plan.inputs().stream().map(ActionPlan.Input::target).toList(),
                plan.invocation() instanceof ActionInvocation.Script ? plan.scriptProgramSpec() : null,
                arguments(plan, arguments, effectId));
    }

    /**
     * Invokes a reconcile adapter from a persisted Effect request snapshot.
     */
    public Object invokeRaw(ReconcilePlan reconcile, Map<String, Object> input, String effectId) throws Exception {
        ReconcilePlan plan = Objects.requireNonNull(reconcile, "reconcile");
        Map<String, Object> request = Objects.requireNonNull(input, "input");
        return invokeRaw(plan.invocation(), plan.inputs().stream().map(ReconcilePlan.Input::declaredType).toList(),
                plan.inputs().stream().map(ReconcilePlan.Input::target).toList(),
                plan.invocation() instanceof ActionInvocation.Script ? plan.scriptProgramSpec() : null,
                reconcileArguments(plan, request, effectId));
    }

    private Object invokeRaw(ActionInvocation invocation, List<String> declaredTypes, List<String> targets,
            ScriptProgramSpec scriptProgram, Object[] arguments) throws Exception {
        if (invocation instanceof ActionInvocation.Java java) {
            return invokeJava(invocation, java, declaredTypes, arguments);
        }
        if (invocation instanceof ActionInvocation.SpringBean springBean) {
            return invokeSpringBean(invocation, springBean, declaredTypes, arguments);
        }
        if (invocation instanceof ActionInvocation.Script) {
            return invokeScript(scriptProgram, targets, arguments);
        }
        throw new IllegalArgumentException(
                "Action invocation requires compiled Java: " + invocation.getClass().getSimpleName());
    }

    /**
     * Applies the semantic return mapping to a raw Action result.
     */
    public Map<String, Object> mapResult(ActionPlan action, Object result) {
        ActionPlan plan = Objects.requireNonNull(action, "action");
        if (plan.output() == null) {
            return Map.of();
        }
        ActionPlan.Output output = plan.output();
        Object mapped = convertValue(convertValue(result, output.resultType()), output.targetType());
        return Collections.singletonMap(output.target(), mapped);
    }

    private Object invokeJava(ActionInvocation invocation, ActionInvocation.Java java, List<String> declaredTypes,
            Object[] arguments) throws Exception {
        String className = java.className();
        Class<?> declaredType = load(className);
        Constructor<?> constructor = declaredType.getDeclaredConstructor();
        if (!constructor.canAccess(null)) {
            throw new IllegalAccessException("Java Action constructor is not accessible: " + className);
        }
        Object target;
        try {
            target = constructor.newInstance();
        } catch (InvocationTargetException failure) {
            throw invocationFailure(failure);
        }
        return invokeMethod(invocation, declaredTypes, target, declaredType, arguments);
    }

    private Object invokeSpringBean(ActionInvocation invocation, ActionInvocation.SpringBean springBean,
            List<String> declaredTypes, Object[] arguments) throws Exception {
        Class<?> declaredType = load(springBean.declaredClass());
        Object target = components.resolve(springBean.beanName(), declaredType);
        return invokeMethod(invocation, declaredTypes, target, declaredType, arguments);
    }

    private Object invokeMethod(ActionInvocation invocation, List<String> declaredTypes, Object target,
            Class<?> declaredType, Object[] arguments) throws Exception {
        Method method = requireMethod(declaredType, invocation, declaredTypes);
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            throw invocationFailure(failure);
        }
    }

    private static Exception invocationFailure(InvocationTargetException failure) {
        Throwable cause = failure.getCause();
        if (cause instanceof Exception exception) {
            return exception;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(cause);
    }

    private Object invokeScript(ScriptProgramSpec spec, List<String> targets, Object[] values) {
        LinkedHashMap<String, Object> context = new LinkedHashMap<>();
        for (int index = 0; index < targets.size(); index++) {
            context.put(targets.get(index), values[index]);
        }
        ScriptProgram program = scriptPrograms.get(spec);
        if (program == null) {
            throw new IllegalStateException("No script program is available for language '" + spec.language() + "'");
        }
        return scripts.evaluate(program, Collections.unmodifiableMap(context));
    }

    private Object[] arguments(ActionPlan action, Map<String, Object> input, String effectId) {
        Object[] result = new Object[action.inputs().size()];
        for (int index = 0; index < action.inputs().size(); index++) {
            ActionPlan.Input mapping = action.inputs().get(index);
            result[index] = convertValue(argumentValue(mapping, input, effectId), mapping.declaredType());
        }
        return result;
    }

    private Object[] reconcileArguments(ReconcilePlan reconcile, Map<String, Object> input, String effectId) {
        Object[] result = new Object[reconcile.inputs().size()];
        for (int index = 0; index < reconcile.inputs().size(); index++) {
            ReconcilePlan.Input mapping = reconcile.inputs().get(index);
            result[index] = convertValue(reconcileArgumentValue(mapping, input, effectId), mapping.declaredType());
        }
        return result;
    }

    private static Object argumentValue(ActionPlan.Input mapping, Map<String, Object> input, String effectId) {
        if (mapping.source() instanceof ActionPlan.InputSource.EffectId) {
            if (effectId == null) {
                throw new IllegalArgumentException("Effect ID is unavailable");
            }
            return effectId;
        }
        if (input.containsKey(mapping.target())) {
            return input.get(mapping.target());
        }
        throw new IllegalArgumentException("Materialized Action input is missing: " + mapping.target());
    }

    private static Object reconcileArgumentValue(ReconcilePlan.Input mapping, Map<String, Object> input,
            String effectId) {
        if (mapping.source() instanceof ReconcilePlan.InputSource.EffectId) {
            if (effectId == null) {
                throw new IllegalArgumentException("Effect ID is unavailable");
            }
            return effectId;
        }
        if (mapping.source() instanceof ReconcilePlan.InputSource.RequestField field && input.containsKey(field.name())) {
            return input.get(field.name());
        }
        throw new IllegalArgumentException(
                "Persisted Effect input is missing: " + ((ReconcilePlan.InputSource.RequestField) mapping.source())
                    .name());
    }

    private Class<?> load(String name) {
        try {
            return Class.forName(name, false, classLoader);
        } catch (ClassNotFoundException failure) {
            throw new IllegalArgumentException("Action class is unavailable: " + name, failure);
        }
    }

    private Method requireMethod(Class<?> declaredType, ActionInvocation invocation, List<String> declaredTypes)
            throws NoSuchMethodException {
        Class<?>[] parameterTypes =
                declaredTypes
            .stream()
            .map(type -> DataTypes.getJavaClass(type, classLoader))
            .toArray(Class<?>[]::new);
        return declaredType.getMethod(method(invocation), parameterTypes);
    }

    private static String declaredClass(ActionInvocation invocation) {
        if (invocation instanceof ActionInvocation.Java java) {
            return java.className();
        }
        if (invocation instanceof ActionInvocation.SpringBean springBean) {
            return springBean.declaredClass();
        }
        throw new IllegalArgumentException(
                "Java Action requires a declared class: " + invocation.getClass().getSimpleName());
    }

    private static String method(ActionInvocation invocation) {
        if (invocation instanceof ActionInvocation.Java java) {
            return java.method();
        }
        if (invocation instanceof ActionInvocation.SpringBean component) {
            return component.method();
        }
        throw new IllegalArgumentException("Invocation does not declare a Java method");
    }

    /**
     * Converts a value against this invoker's exact application ClassLoader.
     */
    public Object convertValue(Object value, String declaredType) {
        return convert(value, declaredType, classLoader);
    }

    private static Object convert(Object value, String declaredType, ClassLoader classLoader) {
        Class<?> type = DataTypes.getJavaClass(declaredType, classLoader);
        Class<?> target = type.isPrimitive() ? DataTypes.getWrapperClass(type) : type;
        Object converted = DataTypes.transfer(value, target);
        if (converted == null && type.isPrimitive()) {
            throw new IllegalArgumentException("null cannot be mapped to primitive " + declaredType);
        }
        return converted;
    }
}
