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
package com.alibaba.compileflow.engine.bpmn.parser;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.bpmn.model.Activity;
import com.alibaba.compileflow.engine.bpmn.model.BpmnElementContainer;
import com.alibaba.compileflow.engine.bpmn.model.BpmnModelConstants;
import com.alibaba.compileflow.engine.bpmn.model.ExtensionElements;
import com.alibaba.compileflow.engine.bpmn.model.LoopCharacteristics;
import com.alibaba.compileflow.engine.bpmn.model.Process;
import com.alibaba.compileflow.engine.bpmn.model.ScriptTask;
import com.alibaba.compileflow.engine.core.xml.parser.AbstractFlowElementParser;
import com.alibaba.compileflow.engine.core.xml.parser.ParseContext;
import com.alibaba.compileflow.engine.core.xml.parser.XmlSource;
import com.alibaba.compileflow.engine.core.xml.parser.AbstractFlowElementParserRegistry;
import com.alibaba.compileflow.engine.core.model.AbstractFlowElement;
import com.alibaba.compileflow.engine.core.model.Element;
import com.alibaba.compileflow.engine.core.model.action.Action;
import com.alibaba.compileflow.engine.core.model.action.ActionExecution;
import com.alibaba.compileflow.engine.core.model.action.InvocationPolicy;
import com.alibaba.compileflow.engine.core.model.action.ActionType;
import com.alibaba.compileflow.engine.core.model.action.EffectPolicy;
import com.alibaba.compileflow.engine.core.model.action.EffectRecovery;
import com.alibaba.compileflow.engine.core.model.action.HasAction;
import com.alibaba.compileflow.engine.core.model.action.EffectiveInvocationPolicy;
import com.alibaba.compileflow.engine.core.model.action.EffectiveEffectPolicy;
import com.alibaba.compileflow.engine.core.model.action.RetryJitter;
import com.alibaba.compileflow.engine.core.model.action.ReconcileAction;
import com.alibaba.compileflow.engine.core.model.action.ReconcileInput;
import com.alibaba.compileflow.engine.core.model.extension.ExtensionAttribute;
import com.alibaba.compileflow.engine.core.model.extension.ExtensionElement;
import com.alibaba.compileflow.engine.core.model.variable.VariableContainer;
import com.alibaba.compileflow.engine.core.model.variable.Variable;
import com.alibaba.compileflow.engine.core.model.mapping.InputMapping;
import com.alibaba.compileflow.engine.core.model.mapping.MappingModel;
import com.alibaba.compileflow.engine.core.model.mapping.OutputMapping;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/**
 * Abstract base class for BPMN element XML parsers.
 *
 * @author yusu
 */
public abstract class AbstractBpmnElementParser<E extends Element> extends AbstractFlowElementParser<E> {
    @Override
    public E parse(XmlSource xmlSource, ParseContext parseContext) throws Exception {
        BpmnAttributeContract.validate(xmlSource);
        return super.parse(xmlSource, parseContext);
    }

    @Override
    protected void parseCommonAttributes(XmlSource xmlSource, E element, ParseContext parseContext) {
        if (element instanceof AbstractFlowElement flowElement) {
            flowElement.setName(xmlSource.getString(BpmnModelConstants.BPMN_ATTRIBUTE_NAME));
        }
    }

    @Override
    public AbstractFlowElementParserRegistry getParserRegistry() {
        return BpmnElementParserRegistry.getInstance();
    }

    @Override
    protected boolean attachPlatformChildElement(Element childElement, E element, ParseContext parseContext) {
        if (childElement instanceof ExtensionElements extensionElements) {
            handleExtensionElements(extensionElements, element);
            return true;
        }
        if (element instanceof BpmnElementContainer container && childElement instanceof AbstractFlowElement flowElement) {
            container.addElement(flowElement);
            return true;
        }
        if (element instanceof HasAction actionOwner && childElement instanceof Action action) {
            if (actionOwner.getAction() != null) {
                throw invalidExtension("A BPMN action owner must declare at most one action");
            }
            actionOwner.setAction(action);
            return true;
        }
        if (element instanceof Activity activity && childElement instanceof LoopCharacteristics loopCharacteristics) {
            if (activity.getLoopCharacteristics() != null) {
                throw invalidExtension("A BPMN activity must declare at most one loop characteristics element");
            }
            activity.setLoopCharacteristics(loopCharacteristics);
            return true;
        }
        return false;
    }

    @Override
    protected void attachChildElement(Element childElement, E element, ParseContext parseContext) {
        if (childElement instanceof IgnoredTextElementParser.IgnoredTextElement) {
            return;
        }
        throw new CompileFlowException(ErrorCode.CF_VALIDATION_002,
                "Unsupported BPMN child element " + childElement.getClass().getSimpleName() + " under "
                + element.getClass().getSimpleName(), null);
    }

    private void handleExtensionElements(ExtensionElements extensionElements, E element) {
        validateExtensionElements(extensionElements, element);
        if (element instanceof VariableContainer varOwner) {
            extensionElements
                .getExtensionElements()
                .stream()
                .filter(ext -> BpmnModelConstants.BPMN_EXT_ATTRIBUTE_VAR.equals(ext.getName()))
                .map(this::buildVar)
                .forEach(varOwner::addVariable);
        }
        if (element instanceof MappingModel mappingOwner) {
            boolean actionBoundary = !(element instanceof com.alibaba.compileflow.engine.bpmn.model.CallActivity);
            extensionElements
                .getExtensionElements()
                .forEach(extension -> {
                    if (BpmnModelConstants.BPMN_EXT_ELEMENT_INPUT.equals(extension.getName())) {
                        mappingOwner.addInputMapping(buildInput(extension));
                    } else if (BpmnModelConstants.BPMN_EXT_ELEMENT_OUTPUT.equals(extension.getName())) {
                        mappingOwner.addOutputMapping(buildOutput(extension, actionBoundary));
                    }
                });
        }
        if (element instanceof HasAction actionOwner) {
            ExtensionElement action = findOnlyExtension(extensionElements, BpmnModelConstants.BPMN_EXT_ATTRIBUTE_ACTION);
            if (action != null) {
                if (actionOwner.getAction() != null) {
                    throw invalidExtension("A BPMN action owner must declare at most one cf:action");
                }
                actionOwner.setAction(buildAction(action));
            }
        }
        if (element instanceof ScriptTask scriptTask) {
            ExtensionElement invocationPolicy =
                    findOnlyExtension(extensionElements, BpmnModelConstants.BPMN_EXT_ELEMENT_INVOCATION_POLICY);
            ExtensionElement effectPolicy =
                    findOnlyExtension(extensionElements, BpmnModelConstants.BPMN_EXT_ELEMENT_EFFECT_POLICY);
            if (invocationPolicy != null) {
                if (scriptTask.getInvocationPolicy() != null) {
                    throw invalidExtension("A BPMN scriptTask must declare at most one cf:invocationPolicy");
                }
                scriptTask.setInvocationPolicy(buildInvocationPolicy(invocationPolicy));
            }
            if (effectPolicy != null) {
                if (scriptTask.getEffectPolicy() != null) {
                    throw invalidExtension("A BPMN scriptTask must declare at most one cf:effectPolicy");
                }
                scriptTask.setEffectPolicy(buildEffectPolicy(effectPolicy));
            }
        }
    }

    private void validateExtensionElements(ExtensionElements extensionElements, E element) {
        for (ExtensionElement extension : extensionElements.getExtensionElements()) {
            if (!BpmnModelConstants.CF_NS.equals(extension.getNamespaceURI())) {
                throw invalidExtension("Unsupported BPMN extension namespace: " + extension.getNamespaceURI());
            }
            String name = extension.getName();
            boolean supported = BpmnModelConstants.BPMN_EXT_ATTRIBUTE_VAR.equals(name) && element instanceof Process
                    || (BpmnModelConstants.BPMN_EXT_ELEMENT_INPUT.equals(name)
                    || BpmnModelConstants.BPMN_EXT_ELEMENT_OUTPUT.equals(name)) && element instanceof MappingModel
                    || BpmnModelConstants.BPMN_EXT_ATTRIBUTE_ACTION.equals(name) && element instanceof HasAction
                    || (BpmnModelConstants.BPMN_EXT_ELEMENT_INVOCATION_POLICY.equals(name)
                    || BpmnModelConstants.BPMN_EXT_ELEMENT_EFFECT_POLICY.equals(name)) && element instanceof ScriptTask;
            if (!supported) {
                throw invalidExtension("Unsupported cf:" + name + " extension on " + element
                            .getClass()
                            .getSimpleName());
            }
        }
    }

    private ExtensionElement findOnlyExtension(ExtensionElements extensionElements, String name) {
        List<ExtensionElement> matches = extensionElements
            .getExtensionElements()
            .stream()
            .filter(extension -> name.equals(extension.getName()))
            .toList();
        if (matches.size() > 1) {
            throw invalidExtension("A BPMN element must declare at most one cf:" + name);
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

    private InvocationPolicy buildInvocationPolicy(ExtensionElement invocationPolicyElement) {
        validateAttributes(invocationPolicyElement,
                Set.of("timeout", "attemptTimeout", "maxAttempts", "initialBackoff", "backoffMultiplier", "maxBackoff",
                        "jitter", "retryOn", "onFailure"));
        requireNoChildren(invocationPolicyElement);
        InvocationPolicy invocationPolicy = new InvocationPolicy();
        invocationPolicy.setTimeout(invocationPolicyElement.getAttributeValue("timeout"));
        invocationPolicy.setAttemptTimeout(invocationPolicyElement.getAttributeValue("attemptTimeout"));
        invocationPolicy.setMaxAttempts(parseIntegerAttribute(invocationPolicyElement, "maxAttempts"));
        invocationPolicy.setInitialBackoff(invocationPolicyElement.getAttributeValue("initialBackoff"));
        invocationPolicy.setBackoffMultiplier(parseDoubleAttribute(invocationPolicyElement, "backoffMultiplier"));
        invocationPolicy.setMaxBackoff(invocationPolicyElement.getAttributeValue("maxBackoff"));
        invocationPolicy.setRetryOn(invocationPolicyElement.getAttributeValue("retryOn"));
        invocationPolicy.setOnFailure(invocationPolicyElement.getAttributeValue("onFailure"));
        try {
            String jitter = invocationPolicyElement.getAttributeValue("jitter");
            invocationPolicy.setJitter(jitter == null ? null : RetryJitter.from(jitter));
            EffectiveInvocationPolicy.from(invocationPolicy);
        } catch (IllegalArgumentException exception) {
            throw invalidExtension("Invalid cf:invocationPolicy: " + exception.getMessage(), exception);
        }
        return invocationPolicy;
    }

    private Integer parseIntegerAttribute(ExtensionElement element, String attributeName) {
        return parseIntegerAttribute(element, attributeName, "invocationPolicy");
    }

    private Integer parseIntegerAttribute(ExtensionElement element, String attributeName, String ownerName) {
        String rawValue = element.getAttributeValue(attributeName);
        if (rawValue == null) {
            return null;
        }
        try {
            return Integer.valueOf(rawValue);
        } catch (NumberFormatException exception) {
            throw invalidExtension("cf:" + ownerName + " attribute '" + attributeName + "' must be an integer",
                    exception);
        }
    }

    private Double parseDoubleAttribute(ExtensionElement element, String attributeName) {
        String rawValue = element.getAttributeValue(attributeName);
        if (rawValue == null) {
            return null;
        }
        try {
            if (!rawValue.equals(rawValue.trim())) {
                throw new NumberFormatException("surrounding whitespace");
            }
            double value = Double.parseDouble(rawValue);
            if (!Double.isFinite(value)) {
                throw new NumberFormatException("value must be finite");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw invalidExtension("cf:invocationPolicy attribute '" + attributeName + "' must be a finite number",
                    exception);
        }
    }

    private Variable buildVar(ExtensionElement varElement) {
        validateAttributes(varElement, Set.of("name", "description", "dataType", "defaultValue", "inOutType"));
        requireNoChildren(varElement);
        Variable var = new Variable();
        var.setName(varElement.getAttributeValue("name"));
        var.setDataType(varElement.getAttributeValue("dataType"));
        var.setDescription(varElement.getAttributeValue("description"));
        var.setDefaultValue(varElement.getAttributeValue("defaultValue"));
        var.setInOutType(varElement.getAttributeValue("inOutType"));
        return var;
    }

    private InputMapping buildInput(ExtensionElement element) {
        validateAttributes(element, Set.of("source", "target", "dataType", "defaultValue"));
        requireNoChildren(element);
        InputMapping input = new InputMapping();
        input.setSource(element.getAttributeValue("source"));
        input.setTarget(element.getAttributeValue("target"));
        input.setDataType(element.getAttributeValue("dataType"));
        input.setDefaultValue(element.getAttributeValue("defaultValue"));
        return input;
    }

    private OutputMapping buildOutput(ExtensionElement element, boolean actionBoundary) {
        validateAttributes(element, actionBoundary ? Set.of("target", "dataType") : Set.of("source", "target"));
        requireNoChildren(element);
        OutputMapping output = new OutputMapping();
        output.setSource(element.getAttributeValue("source"));
        output.setTarget(element.getAttributeValue("target"));
        output.setDataType(element.getAttributeValue("dataType"));
        return output;
    }

    private Action buildAction(ExtensionElement extensionElement) {
        Action action = new Action();
        String typeValue = extensionElement.getAttributeValue("type");
        if (StringUtils.isBlank(typeValue)) {
            throw invalidExtension("A BPMN action must declare a non-blank type");
        }
        ActionType actionType;
        try {
            actionType = ActionType.of(typeValue);
        } catch (IllegalArgumentException exception) {
            throw invalidExtension(exception.getMessage(), exception);
        }
        validateActionAttributes(actionType, extensionElement, true);
        action.setType(actionType);
        try {
            action.setExecution(ActionExecution.of(extensionElement.getAttributeValue("execution")));
        } catch (IllegalArgumentException exception) {
            throw invalidExtension(exception.getMessage(), exception);
        }

        ActionChildren children = parseActionChildren(extensionElement);
        if (actionType != ActionType.SCRIPT && !children.codeElements().isEmpty()) {
            throw invalidExtension("cf:code is only supported by BPMN script actions");
        }
        List<ExtensionElement> effectPolicies = children.effectPolicies();
        List<ExtensionElement> invocationPolicies = children.invocationPolicies();
        if (effectPolicies.size() > 1) {
            throw invalidExtension("A BPMN action must contain at most one cf:effectPolicy");
        }
        if (invocationPolicies.size() > 1) {
            throw invalidExtension("A BPMN action must contain at most one cf:invocationPolicy");
        }
        populateAction(action, extensionElement);
        action.setInputMappings(children.inputs());
        action.setOutputMappings(children.outputs());
        if (!invocationPolicies.isEmpty()) {
            action.setInvocationPolicy(buildInvocationPolicy(invocationPolicies.get(0)));
        }
        if (!effectPolicies.isEmpty()) {
            action.setEffectPolicy(buildEffectPolicy(effectPolicies.get(0)));
        }
        return action;
    }

    private ReconcileAction buildReconcileAction(ExtensionElement extensionElement) {
        String typeValue = extensionElement.getAttributeValue("type");
        if (StringUtils.isBlank(typeValue)) {
            throw invalidExtension("A BPMN reconcile action must declare a non-blank type");
        }
        ActionType actionType;
        try {
            actionType = ActionType.of(typeValue);
        } catch (IllegalArgumentException exception) {
            throw invalidExtension(exception.getMessage(), exception);
        }
        validateActionAttributes(actionType, extensionElement, false);

        ReconcileAction action = new ReconcileAction();
        action.setType(actionType);
        List<ExtensionElement> codeElements = new ArrayList<>();
        for (ExtensionElement child : extensionElement.getChildElements()) {
            if (!BpmnModelConstants.CF_NS.equals(child.getNamespaceURI())) {
                throw invalidExtension("Unsupported BPMN reconcile action child namespace: " + child.getNamespaceURI());
            }
            if (BpmnModelConstants.BPMN_EXT_ELEMENT_INPUT.equals(child.getName())) {
                action.addInput(buildReconcileInput(child));
            } else if (BpmnModelConstants.BPMN_EXT_ATTRIBUTE_CODE.equals(child.getName())) {
                validateAttributes(child, Set.of());
                requireNoChildren(child);
                codeElements.add(child);
            } else {
                throw invalidExtension("Unsupported cf:" + child.getName() + " child for BPMN reconcile action");
            }
        }
        if (codeElements.size() > 1) {
            throw invalidExtension("A BPMN reconcile action must contain at most one cf:code");
        }
        if (actionType != ActionType.SCRIPT && !codeElements.isEmpty()) {
            throw invalidExtension("cf:code is only supported by BPMN script reconcile actions");
        }
        populateReconcileAction(action, extensionElement);
        return action;
    }

    private ReconcileInput buildReconcileInput(ExtensionElement element) {
        validateAttributes(element, Set.of("source", "target", "dataType"));
        requireNoChildren(element);
        ReconcileInput input = new ReconcileInput();
        input.setSource(element.getAttributeValue("source"));
        input.setTarget(element.getAttributeValue("target"));
        input.setDataType(element.getAttributeValue("dataType"));
        return input;
    }

    private ActionChildren parseActionChildren(ExtensionElement action) {
        List<InputMapping> inputs = new ArrayList<>();
        List<OutputMapping> outputs = new ArrayList<>();
        List<ExtensionElement> codeElements = new ArrayList<>();
        List<ExtensionElement> effectPolicies = new ArrayList<>();
        List<ExtensionElement> invocationPolicies = new ArrayList<>();
        for (ExtensionElement child : action.getChildElements()) {
            if (!BpmnModelConstants.CF_NS.equals(child.getNamespaceURI())) {
                throw invalidExtension("Unsupported BPMN action child namespace: " + child.getNamespaceURI());
            }
            if (BpmnModelConstants.BPMN_EXT_ELEMENT_INPUT.equals(child.getName())) {
                inputs.add(buildInput(child));
            } else if (BpmnModelConstants.BPMN_EXT_ELEMENT_OUTPUT.equals(child.getName())) {
                outputs.add(buildOutput(child, true));
            } else if (BpmnModelConstants.BPMN_EXT_ATTRIBUTE_CODE.equals(child.getName())) {
                validateAttributes(child, Set.of());
                requireNoChildren(child);
                codeElements.add(child);
            } else if (BpmnModelConstants.BPMN_EXT_ELEMENT_EFFECT_POLICY.equals(child.getName())) {
                effectPolicies.add(child);
            } else if (BpmnModelConstants.BPMN_EXT_ELEMENT_INVOCATION_POLICY.equals(child.getName())) {
                invocationPolicies.add(child);
            } else {
                throw invalidExtension("Unsupported cf:" + child.getName() + " child for BPMN action");
            }
        }
        if (codeElements.size() > 1) {
            throw invalidExtension("A BPMN action must contain at most one cf:code");
        }
        return new ActionChildren(List.copyOf(inputs), List.copyOf(outputs), List.copyOf(codeElements),
                List.copyOf(effectPolicies), List.copyOf(invocationPolicies));
    }

    private void populateAction(Action action, ExtensionElement extension) {
        switch (action.getType()) {
            case SPRING_BEAN -> {
                action.setBean(extension.getAttributeValue("bean"));
                action.setClassName(extension.getAttributeValue("class"));
                action.setMethod(extension.getAttributeValue("method"));
            }
            case JAVA -> {
                action.setClassName(extension.getAttributeValue("class"));
                action.setMethod(extension.getAttributeValue("method"));
            }
            case SCRIPT -> {
                action.setLanguage(extension.getAttributeValue("language"));
                action.setSource(requireCode(extension).getTextContent());
            }
        }
    }

    private void populateReconcileAction(ReconcileAction action, ExtensionElement extension) {
        switch (action.getType()) {
            case SPRING_BEAN -> {
                action.setBean(extension.getAttributeValue("bean"));
                action.setClassName(extension.getAttributeValue("class"));
                action.setMethod(extension.getAttributeValue("method"));
            }
            case JAVA -> {
                action.setClassName(extension.getAttributeValue("class"));
                action.setMethod(extension.getAttributeValue("method"));
            }
            case SCRIPT -> {
                action.setLanguage(extension.getAttributeValue("language"));
                action.setSource(requireCode(extension).getTextContent());
            }
        }
    }

    private record ActionChildren(List<InputMapping> inputs, List<OutputMapping> outputs,
            List<ExtensionElement> codeElements, List<ExtensionElement> effectPolicies,
            List<ExtensionElement> invocationPolicies) {}

    private EffectPolicy buildEffectPolicy(ExtensionElement element) {
        validateAttributes(element,
                Set.of("recoveryPlanVariable", "recovery", "maxAttempts", "maxReconcileAttempts", "recoveryDelay",
                        "maxRecoveryDuration"));
        List<ExtensionElement> reconcileActions = element
            .getChildElements()
            .stream()
            .filter(child -> BpmnModelConstants.BPMN_EXT_ELEMENT_RECONCILE_ACTION.equals(child.getName()))
            .filter(child -> BpmnModelConstants.CF_NS.equals(child.getNamespaceURI()))
            .toList();
        if (reconcileActions.size() > 1 || reconcileActions.size() != element.getChildElements().size()) {
            throw invalidExtension("cf:effectPolicy may contain at most one cf:reconcileAction");
        }
        EffectPolicy policy = new EffectPolicy();
        try {
            policy.setRecoveryPlanVariable(element.getAttributeValue("recoveryPlanVariable"));
            String recovery = element.getAttributeValue("recovery");
            if (recovery != null) {
                policy.setRecovery(EffectRecovery.of(recovery));
            }
            policy.setMaxAttempts(parseIntegerAttribute(element, "maxAttempts", "effectPolicy"));
            policy.setMaxReconcileAttempts(parseIntegerAttribute(element, "maxReconcileAttempts", "effectPolicy"));
            policy.setRecoveryDelay(element.getAttributeValue("recoveryDelay"));
            policy.setMaxRecoveryDuration(element.getAttributeValue("maxRecoveryDuration"));
            if (!reconcileActions.isEmpty()) {
                policy.setReconcileAction(buildReconcileAction(reconcileActions.get(0)));
            }
            EffectiveEffectPolicy.from(policy);
        } catch (IllegalArgumentException exception) {
            throw invalidExtension("Invalid cf:effectPolicy: " + exception.getMessage(), exception);
        }
        return policy;
    }

    private void validateActionAttributes(ActionType actionType, ExtensionElement action, boolean allowExecution) {
        Set<String> common = allowExecution ? Set.of("type", "execution") : Set.of("type");
        switch (actionType) {
            case SPRING_BEAN -> validateAttributes(action, with(common, "bean", "class", "method"));
            case JAVA -> validateAttributes(action, with(common, "class", "method"));
            case SCRIPT -> validateAttributes(action, with(common, "language"));
        }
    }

    private Set<String> with(Set<String> common, String... additional) {
        Set<String> names = new java.util.HashSet<>(common);
        names.addAll(List.of(additional));
        return Set.copyOf(names);
    }

    private ExtensionElement requireCode(ExtensionElement action) {
        List<ExtensionElement> codeElements = action.getChildElements(BpmnModelConstants.BPMN_EXT_ATTRIBUTE_CODE);
        if (codeElements.size() != 1) {
            throw invalidExtension("Script actions must contain exactly one cf:code element");
        }
        return codeElements.get(0);
    }

    private void validateAttributes(ExtensionElement element, Set<String> supportedNames) {
        for (ExtensionAttribute attribute : element.getAttributes()) {
            if (StringUtils.isNotBlank(attribute.getNamespace())
                    || StringUtils.isNotBlank(attribute.getNamespacePrefix())
                    || !supportedNames.contains(attribute.getName())) {
                throw invalidExtension("Unsupported attribute on cf:" + element.getName() + ": " + attribute.getName());
            }
        }
    }

    private void requireNoChildren(ExtensionElement element) {
        if (!element.getChildElements().isEmpty()) {
            throw invalidExtension("cf:" + element.getName() + " must not contain child elements");
        }
    }

    private CompileFlowException invalidExtension(String message) {
        return invalidExtension(message, null);
    }

    private CompileFlowException invalidExtension(String message, Throwable cause) {
        return new CompileFlowException(ErrorCode.CF_VALIDATION_002, message, cause);
    }
}
