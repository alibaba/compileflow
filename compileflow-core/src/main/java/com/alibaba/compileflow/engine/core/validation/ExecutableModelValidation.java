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
package com.alibaba.compileflow.engine.core.validation;

import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.core.java.naming.JavaNames;
import com.alibaba.compileflow.engine.core.semantic.naming.ProcessNames;
import com.alibaba.compileflow.engine.core.model.FlowModel;
import com.alibaba.compileflow.engine.core.model.ProcessCallModel;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessCallTarget;
import com.alibaba.compileflow.engine.core.model.ProcessVariableContainer;
import com.alibaba.compileflow.engine.core.model.action.Action;
import com.alibaba.compileflow.engine.core.model.action.ActionExecution;
import com.alibaba.compileflow.engine.core.model.action.InvocationPolicy;
import com.alibaba.compileflow.engine.core.model.action.ActionType;
import com.alibaba.compileflow.engine.core.model.action.EffectiveEffectPolicy;
import com.alibaba.compileflow.engine.core.model.action.EffectiveInvocationPolicy;
import com.alibaba.compileflow.engine.core.model.action.EffectMetadata;
import com.alibaba.compileflow.engine.core.model.action.ReconcileAction;
import com.alibaba.compileflow.engine.core.model.action.ReconcileInput;
import com.alibaba.compileflow.engine.core.model.mapping.InputMapping;
import com.alibaba.compileflow.engine.core.model.mapping.MappingModel;
import com.alibaba.compileflow.engine.core.model.mapping.OutputMapping;
import com.alibaba.compileflow.engine.core.model.variable.Variable;
import com.alibaba.compileflow.engine.core.type.DataTypes;
import com.alibaba.compileflow.engine.spi.script.ScriptExecutor;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/**
 * Shared validation for model values that are compiled into executable Java.
 *
 * @author yusu
 */
public final class ExecutableModelValidation {
    private ExecutableModelValidation() {
    }

    /**
     * Validates variables declared by an executable process.
     *
     * @param modelKind human-readable model kind used in validation messages
     * @param model     executable model
     * @param messages  destination for validation failures
     * @return valid declared variable names
     */
    public static Set<String> validateProcessVariables(String modelKind, FlowModel<?> model,
            List<ValidationFailure> messages) {
        Set<String> variableNames = new LinkedHashSet<>();
        List<Variable> variables = model.getVariables();
        if (variables == null) {
            messages.add(failure(modelKind, "process variables must not be null"));
            return variableNames;
        }

        for (Variable variable : variables) {
            if (variable == null) {
                messages.add(failure(modelKind, "process variables must not contain null"));
                continue;
            }
            String name = variable.getName();
            if (!ProcessNames.isIdentifier(name)) {
                messages.add(failure(modelKind, "process variable has invalid Java name: " + name));
            } else {
                variableNames.add(name);
            }
            validateDataType(modelKind, "process variable " + name, variable.getDataType(), messages);
            String direction = variable.getInOutType();
            if (!isProcessDirection(direction)) {
                messages.add(
                        failure(modelKind,
                                "process variable has invalid inOutType, name=" + name + ", inOutType=" + direction));
            }
            if (variable.getDefaultValue() != null) {
                validateDefaultValue(modelKind, "process variable " + name, variable, messages);
            }
        }
        return variableNames;
    }

    /**
     * Validates an executable action and its mapped variables.
     *
     * @param modelKind            human-readable model kind used in validation messages
     * @param location             action location within the model
     * @param action               action to validate; {@code null} is accepted
     * @param processVariableNames valid process-level variable names
     * @param messages             destination for validation failures
     */
    public static void validateAction(String modelKind, String location, Action action, Set<String> processVariableNames,
            List<ValidationFailure> messages) {
        if (action == null) {
            return;
        }
        ActionType type = action.getType();
        if (type == null) {
            messages.add(failure(modelKind, location + " must declare a type"));
            return;
        }

        validateActionMappings(modelKind, location + " mappings", action, processVariableNames, messages);
        validateEffectInputSources(modelKind, location, action, messages);
        switch (type) {
            case JAVA -> validateJavaAction(modelKind, location, action, messages);
            case SPRING_BEAN -> validateSpringBeanAction(modelKind, location, action, messages);
            case SCRIPT -> validateScriptAction(modelKind, location, action, messages);
        }
    }

    private static void validateEffectInputSources(String modelKind, String location, Action action,
            List<ValidationFailure> messages) {
        action
            .getInputMappings()
            .stream()
            .filter(java.util.Objects::nonNull)
            .forEach(input -> {
                String source = input.getSource();
                if (EffectMetadata.isSource(source) && action.getExecution() != ActionExecution.EFFECT) {
                    messages.add(failure(modelKind, location + " Effect ID input requires execution=\"effect\""));
                } else if (EffectMetadata.isReservedSource(source) && !EffectMetadata.isSource(source)) {
                    messages.add(
                            failure(modelKind, location + " has unsupported Effect metadata input source: " + source));
                }
            });
    }

    /**
     * Validates the relationship between an action and its optional Durable effect policy.
     *
     * @param modelKind            human-readable model kind used in validation messages
     * @param location             action location within the model
     * @param action               action to validate; {@code null} is accepted
     * @param messages             destination for validation failures
     */
    public static void validateEffectPolicy(String modelKind, String location, Action action,
            List<ValidationFailure> messages) {
        if (action == null || action.getEffectPolicy() == null) {
            return;
        }
        if (action.getExecution() != ActionExecution.EFFECT) {
            messages.add(failure(modelKind, location + " effectPolicy requires execution=\"effect\""));
            return;
        }
        try {
            EffectiveEffectPolicy.from(action.getEffectPolicy());
        } catch (RuntimeException failure) {
            messages.add(failure(modelKind, location + " has invalid effectPolicy: " + failure.getMessage()));
            return;
        }
        ReconcileAction reconcileAction = action.getEffectPolicy().getReconcileAction();
        if (reconcileAction != null) {
            validateReconcileAction(modelKind, location + " reconcileAction", action, reconcileAction, messages);
        }
    }

    private static void validateReconcileAction(String modelKind, String location, Action effectAction,
            ReconcileAction reconcileAction, List<ValidationFailure> messages) {
        ActionType type = reconcileAction.getType();
        if (type == null) {
            messages.add(failure(modelKind, location + " must declare a type"));
            return;
        }
        validateReconcileInputs(modelKind, location, effectAction, reconcileAction.getInputs(), messages);
        switch (type) {
            case JAVA -> {
                validateJavaClassName(modelKind, location, "class", reconcileAction.getClassName(), messages);
                validateJavaMethod(modelKind, location, reconcileAction.effectiveMethod(), messages);
                validateAbsent(modelKind, location, reconcileAction.getBean(), "bean", messages);
                validateAbsent(modelKind, location, reconcileAction.getLanguage(), "language", messages);
                validateAbsent(modelKind, location, reconcileAction.getSource(), "script source", messages);
            }
            case SPRING_BEAN -> {
                if (StringUtils.isBlank(reconcileAction.getBean())) {
                    messages.add(failure(modelKind, location + " must declare a non-blank bean"));
                } else {
                    validateExactIdentity(modelKind, location + " bean", reconcileAction.getBean(), messages);
                }
                validateJavaClassName(modelKind, location, "class", reconcileAction.getClassName(), messages);
                validateJavaMethod(modelKind, location, reconcileAction.effectiveMethod(), messages);
                validateAbsent(modelKind, location, reconcileAction.getLanguage(), "language", messages);
                validateAbsent(modelKind, location, reconcileAction.getSource(), "script source", messages);
            }
            case SCRIPT -> {
                if (StringUtils.isBlank(reconcileAction.getLanguage())) {
                    messages.add(failure(modelKind, location + " must declare a non-blank script language"));
                } else {
                    try {
                        ScriptExecutor.requireCanonicalName(reconcileAction.getLanguage());
                    } catch (RuntimeException exception) {
                        messages.add(
                                failure(modelKind, location + " has invalid script language: " + exception.getMessage()));
                    }
                }
                if (StringUtils.isBlank(reconcileAction.getSource())) {
                    messages.add(failure(modelKind, location + " must declare non-blank script source"));
                }
                validateAbsent(modelKind, location, reconcileAction.getClassName(), "class", messages);
                validateAbsent(modelKind, location, reconcileAction.getMethod(), "method", messages);
                validateAbsent(modelKind, location, reconcileAction.getBean(), "bean", messages);
            }
        }
    }

    private static void validateReconcileInputs(String modelKind, String location, Action effectAction,
            List<ReconcileInput> inputs, List<ValidationFailure> messages) {
        if (inputs == null) {
            messages.add(failure(modelKind, location + " inputs must not be null"));
            return;
        }
        Set<String> requestFields = effectAction
            .getInputMappings()
            .stream()
            .filter(java.util.Objects::nonNull)
            .filter(input -> !EffectMetadata.isSource(input.getSource()))
            .map(InputMapping::getTarget)
            .collect(java.util.stream.Collectors.toSet());
        Set<String> targets = new LinkedHashSet<>();
        for (ReconcileInput input : inputs) {
            if (input == null) {
                messages.add(failure(modelKind, location + " inputs must not contain null"));
                continue;
            }
            String source = input.getSource();
            if (!ProcessNames.isIdentifier(source)) {
                messages.add(failure(modelKind, location + " input source must be a valid field name: " + source));
            } else if (EffectMetadata.isReservedSource(source) && !EffectMetadata.isSource(source)) {
                messages.add(failure(modelKind, location + " has unsupported Effect metadata input source: " + source));
            } else if (!EffectMetadata.isSource(source) && !requestFields.contains(source)) {
                messages.add(failure(modelKind, location + " input references unknown Effect request field: " + source));
            }
            String target = input.getTarget();
            validateBindingName(modelKind, location + " input target", target, messages);
            if (StringUtils.isNotBlank(target) && !targets.add(target)) {
                messages.add(failure(modelKind, location + " has duplicate input target: " + target));
            }
            validateDataType(modelKind, location + " input " + target, input.getDataType(), messages);
        }
    }

    /**
     * Validates input and output mappings for an executable boundary.
     *
     * @param modelKind            human-readable model kind used in validation messages
     * @param location             mapping location within the model
     * @param mappings             mapped variables
     * @param processVariableNames valid process-level variable names
     * @param messages             destination for validation failures
     */
    public static void validateActionMappings(String modelKind, String location, MappingModel mappings,
            Set<String> processVariableNames, List<ValidationFailure> messages) {
        validateInputMappings(modelKind, location, mappings.getInputMappings(), true, messages);
        validateOutputMappings(modelKind, location, mappings.getOutputMappings(), processVariableNames, true, true,
                messages);
    }

    /**
     * Validates mappings at a synchronous called-process boundary.
     *
     * <p>Unlike an action invocation, a called process returns a variable map and may therefore
     * map any number of explicitly declared child outputs back to distinct parent variables.
     *
     * @param modelKind            human-readable model kind used in validation messages
     * @param location             mapping location within the model
     * @param mappings             called-process input and output mappings
     * @param processVariableNames valid process-level variable names
     * @param messages             destination for validation failures
     */
    public static void validateCalledProcessMappings(String modelKind, String location, MappingModel mappings,
            Set<String> processVariableNames, List<ValidationFailure> messages) {
        validateInputMappings(modelKind, location, mappings.getInputMappings(), false, messages);
        validateOutputMappings(modelKind, location, mappings.getOutputMappings(), processVariableNames, false, false,
                messages);
    }

    /**
     * Validates a logical called-process identity.
     *
     * @param modelKind human-readable model kind used in validation messages
     * @param location  called-process location
     * @param call      process-call definition
     * @param messages  destination for validation failures
     */
    public static void validateCalledProcessReference(String modelKind, String location, ProcessCallModel call,
            List<ValidationFailure> messages) {
        if (call == null || call.getCalledProcessCode() == null) {
            messages.add(failure(modelKind, location + " has an invalid process reference: code is required"));
            return;
        }
        try {
            ProcessIdentifiers.requireCode(call.getCalledProcessCode());
            ProcessCallTarget.from(call.getCalledProcessClasspath(), call.getCalledProcessVersion());
        } catch (IllegalArgumentException exception) {
            messages.add(failure(modelKind, location + " has an invalid process reference: " + exception.getMessage()));
        }
    }

    private static void validateInputMappings(String modelKind, String location, List<InputMapping> inputs,
            boolean dataTypeRequired, List<ValidationFailure> messages) {
        if (inputs == null) {
            messages.add(failure(modelKind, location + " inputs must not be null"));
            return;
        }
        Set<String> targets = new LinkedHashSet<>();
        for (InputMapping input : inputs) {
            if (input == null) {
                messages.add(failure(modelKind, location + " inputs must not contain null"));
                continue;
            }
            String target = input.getTarget();
            validateBindingName(modelKind, location + " input target", target, messages);
            if (StringUtils.isNotBlank(target) && !targets.add(target)) {
                messages.add(failure(modelKind, location + " has duplicate input target: " + target));
            }
            if (dataTypeRequired) {
                validateDataType(modelKind, location + " input " + target, input.getDataType(), messages);
            } else if (input.getDataType() != null) {
                messages.add(failure(modelKind, location + " called-process input must not declare dataType"));
            }
            boolean hasSource = input.getSource() != null;
            boolean hasDefault = input.getDefaultValue() != null;
            if (hasSource == hasDefault) {
                messages.add(
                        failure(modelKind,
                                location + " input must declare exactly one of source or defaultValue, target=" + target));
            } else if (hasSource && StringUtils.isBlank(input.getSource())) {
                messages.add(failure(modelKind, location + " input source must not be blank, target=" + target));
            } else if (hasDefault && dataTypeRequired) {
                validateDefaultValue(modelKind, location + " input " + target, input.getDataType(),
                        input.getDefaultValue(), messages);
            }
        }
    }

    private static void validateOutputMappings(String modelKind, String location, List<OutputMapping> outputs,
            Set<String> processVariableNames, boolean implicitSource, boolean dataTypeRequired,
            List<ValidationFailure> messages) {
        if (outputs == null) {
            messages.add(failure(modelKind, location + " outputs must not be null"));
            return;
        }
        if (implicitSource && outputs.size() > 1) {
            messages.add(failure(modelKind, location + " must declare at most one output mapping"));
        }
        Set<String> sources = new LinkedHashSet<>();
        Set<String> targets = new LinkedHashSet<>();
        for (OutputMapping output : outputs) {
            if (output == null) {
                messages.add(failure(modelKind, location + " outputs must not contain null"));
                continue;
            }
            String source = output.getSource();
            if (implicitSource) {
                if (source != null) {
                    messages.add(failure(modelKind, location + " action output must not declare source"));
                }
            } else {
                validateBindingName(modelKind, location + " output source", source, messages);
                if (StringUtils.isNotBlank(source) && !sources.add(source)) {
                    messages.add(failure(modelKind, location + " has duplicate output source: " + source));
                }
            }
            String target = output.getTarget();
            validateBindingName(modelKind, location + " output target", target, messages);
            if (StringUtils.isNotBlank(target) && !processVariableNames.contains(target)) {
                messages.add(
                        failure(modelKind,
                                location + " output target must reference a declared process variable: " + target));
            } else if (StringUtils.isNotBlank(target) && !targets.add(target)) {
                messages.add(failure(modelKind, location + " has duplicate output target: " + target));
            }
            if (dataTypeRequired) {
                validateDataType(modelKind, location + " output " + target, output.getDataType(), messages);
            } else if (output.getDataType() != null) {
                messages.add(failure(modelKind, location + " called-process output must not declare dataType"));
            }
        }
    }

    private static void validateBindingName(String modelKind, String location, String value,
            List<ValidationFailure> messages) {
        if (!ProcessNames.isIdentifier(value)) {
            messages.add(failure(modelKind, location + " must be a valid Java identifier: " + value));
        } else if (ProcessNames.isReserved(value)) {
            messages.add(failure(modelKind, location + " must not be reserved: " + value));
        }
    }

    /**
     * Validates an authoring-layer invocation policy.
     *
     * @param modelKind human-readable model kind used in validation messages
     * @param location  policy location within the model
     * @param policy    policy to validate; {@code null} is accepted
     * @param messages  destination for validation failures
     */
    public static void validateInvocationPolicy(String modelKind, String location, InvocationPolicy policy,
            List<ValidationFailure> messages) {
        if (policy == null) {
            return;
        }
        try {
            EffectiveInvocationPolicy.from(policy);
        } catch (IllegalArgumentException exception) {
            messages.add(failure(modelKind, location + " is invalid: " + exception.getMessage()));
        }
    }

    private static boolean isProcessDirection(String direction) {
        return ProcessVariableContainer.VARIABLE_TYPE_PARAM.equals(direction)
                || ProcessVariableContainer.VARIABLE_TYPE_INNER.equals(direction)
                || ProcessVariableContainer.VARIABLE_TYPE_RETURN.equals(direction);
    }

    private static void validateJavaAction(String modelKind, String location, Action action,
            List<ValidationFailure> messages) {
        validateJavaClassName(modelKind, location, "class", action.getClassName(), messages);
        validateJavaMethod(modelKind, location, action.effectiveMethod(), messages);
        validateAbsent(modelKind, location, action.getBean(), "bean", messages);
        validateAbsent(modelKind, location, action.getLanguage(), "language", messages);
        validateAbsent(modelKind, location, action.getSource(), "script source", messages);
    }

    private static void validateSpringBeanAction(String modelKind, String location, Action action,
            List<ValidationFailure> messages) {
        if (StringUtils.isBlank(action.getBean())) {
            messages.add(failure(modelKind, location + " must declare a non-blank bean"));
        } else {
            validateExactIdentity(modelKind, location + " bean", action.getBean(), messages);
        }
        validateJavaClassName(modelKind, location, "class", action.getClassName(), messages);
        validateJavaMethod(modelKind, location, action.effectiveMethod(), messages);
        validateAbsent(modelKind, location, action.getLanguage(), "language", messages);
        validateAbsent(modelKind, location, action.getSource(), "script source", messages);
    }

    private static void validateScriptAction(String modelKind, String location, Action action,
            List<ValidationFailure> messages) {
        if (StringUtils.isBlank(action.getLanguage())) {
            messages.add(failure(modelKind, location + " must declare a non-blank script language"));
        } else {
            try {
                ScriptExecutor.requireCanonicalName(action.getLanguage());
            } catch (RuntimeException exception) {
                messages.add(failure(modelKind, location + " has invalid script language: " + exception.getMessage()));
            }
        }
        if (StringUtils.isBlank(action.getSource())) {
            messages.add(failure(modelKind, location + " must declare non-blank script source"));
        }
        validateAbsent(modelKind, location, action.getClassName(), "class", messages);
        validateAbsent(modelKind, location, action.getMethod(), "method", messages);
        validateAbsent(modelKind, location, action.getBean(), "bean", messages);
    }

    private static void validateAbsent(String modelKind, String location, String value, String property,
            List<ValidationFailure> messages) {
        if (value != null) {
            messages.add(failure(modelKind, location + " must not declare " + property + " for its action type"));
        }
    }

    private static void validateJavaClassName(String modelKind, String location, String property, String className,
            List<ValidationFailure> messages) {
        if (!JavaNames.isClassName(className)) {
            messages.add(failure(modelKind, location + " has invalid " + property + ": " + className));
        }
    }

    private static void validateJavaMethod(String modelKind, String location, String method,
            List<ValidationFailure> messages) {
        if (!JavaNames.isIdentifier(method)) {
            messages.add(failure(modelKind, location + " has invalid Java method: " + method));
        }
    }

    private static void validateDataType(String modelKind, String location, String dataType,
            List<ValidationFailure> messages) {
        if (StringUtils.isBlank(dataType)) {
            messages.add(failure(modelKind, location + " dataType must not be blank"));
            return;
        }
        if (!dataType.equals(dataType.trim())) {
            messages.add(failure(modelKind, location + " dataType must not contain surrounding whitespace"));
            return;
        }
        try {
            DataTypes.getJavaClass(dataType);
        } catch (RuntimeException exception) {
            messages.add(
                    failure(modelKind,
                            location + " has unsupported dataType " + dataType + ": " + exception.getMessage()));
        }
    }

    private static void validateExactIdentity(String modelKind, String location, String value,
            List<ValidationFailure> messages) {
        if (!value.equals(value.trim())) {
            messages.add(failure(modelKind, location + " must not contain surrounding whitespace"));
        } else if (value.chars().anyMatch(Character::isISOControl)) {
            messages.add(failure(modelKind, location + " must not contain control characters"));
        }
    }

    private static void validateDefaultValue(String modelKind, String location, Variable variable,
            List<ValidationFailure> messages) {
        validateDefaultValue(modelKind, location, variable.getDataType(), variable.getDefaultValue(), messages);
    }

    private static void validateDefaultValue(String modelKind, String location, String dataType, String defaultValue,
            List<ValidationFailure> messages) {
        if (StringUtils.isBlank(dataType)) {
            return;
        }
        try {
            DataTypes.generateDefaultValueCode(DataTypes.getJavaClass(dataType), defaultValue);
        } catch (RuntimeException exception) {
            messages.add(failure(modelKind, location + " has invalid defaultValue: " + exception.getMessage()));
        }
    }

    private static ValidationFailure failure(String modelKind, String message) {
        return ValidationFailure.of(modelKind + " " + message);
    }
}
