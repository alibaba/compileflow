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
package com.alibaba.compileflow.engine.core.builder.generator;

import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.ProcessSource;
import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.ErrorCode;
import com.alibaba.compileflow.engine.core.builder.generator.analyzer.ProcessGraph;
import com.alibaba.compileflow.engine.core.builder.generator.analyzer.ProcessGraphAnalyzer;
import com.alibaba.compileflow.engine.core.builder.generator.code.ClassTarget;
import com.alibaba.compileflow.engine.core.builder.generator.code.MethodTarget;
import com.alibaba.compileflow.engine.core.builder.generator.code.ParamTarget;
import com.alibaba.compileflow.engine.core.builder.generator.constants.MethodConstants;
import com.alibaba.compileflow.engine.core.builder.generator.constants.Modifier;
import com.alibaba.compileflow.engine.core.builder.generator.method.StatefulTriggerMethodCodeGenerator;
import com.alibaba.compileflow.engine.core.builder.generator.method.TriggerMethodGenerator;
import com.alibaba.compileflow.engine.core.builder.generator.provider.NodeGeneratorProvider;
import com.alibaba.compileflow.engine.core.builder.generator.util.FlowClassUtils;
import com.alibaba.compileflow.engine.core.definition.FlowModel;
import com.alibaba.compileflow.engine.core.definition.GatewayElement;
import com.alibaba.compileflow.engine.core.definition.StatefulElement;
import com.alibaba.compileflow.engine.core.definition.TransitionNode;
import com.alibaba.compileflow.engine.core.definition.var.IVar;
import com.alibaba.compileflow.engine.core.extension.ExtensionInvoker;
import com.alibaba.compileflow.engine.core.infrastructure.ClassWrapper;
import com.alibaba.compileflow.engine.core.infrastructure.bean.BeanProvider;
import com.alibaba.compileflow.engine.core.infrastructure.type.DataType;
import com.alibaba.compileflow.engine.core.infrastructure.utils.ClassUtils;
import com.alibaba.compileflow.engine.core.infrastructure.utils.ProcessUtils;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContextHolder;
import com.alibaba.compileflow.engine.core.runtime.executor.*;
import com.alibaba.compileflow.engine.core.runtime.executor.failure.FailureHandlerResolver;
import com.alibaba.compileflow.engine.core.runtime.executor.failure.FailureHandlers;
import com.alibaba.compileflow.engine.core.runtime.executor.policy.ExecutionPolicy;
import com.alibaba.compileflow.engine.core.runtime.executor.retry.RetryPolicies;
import com.alibaba.compileflow.engine.core.runtime.executor.retry.RetryPolicyResolver;
import com.alibaba.compileflow.engine.core.runtime.instance.ProcessInstance;
import com.alibaba.compileflow.engine.core.runtime.instance.StatefulProcessInstance;
import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static java.util.Objects.requireNonNull;

/**
 * Base for process code generators.
 * <p>
 * Transforms a {@link FlowModel} into executable Java source by building a
 * {@link ProcessGraph}, configuring a {@link ClassTarget}, generating methods
 * (execute/trigger), and formatting the result.
 *
 * @author yusu
 */
public abstract class AbstractProcessCodeGenerator implements ProcessCodeGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractProcessCodeGenerator.class);

    private static final String CONTEXT_VAR_NAME = "_pContext";
    private static final String RESULT_VAR_NAME = "_pResult";
    private static final String WRAP_RESULT_METHOD_NAME = "_wrapResult";

    private static final Formatter FORMATTER = new Formatter();

    private final FlowModel flowModel;
    private ClassTarget classTarget;

    public AbstractProcessCodeGenerator(FlowModel flowModel) {
        this.flowModel = requireNonNull(flowModel, "flowModel");
        resetClassTarget();
    }

    @Override
    public String generateCode() {
        resetClassTarget();

        ProcessGraph processGraph = ExtensionInvoker.getInstance().invokeFirst(
                ProcessGraphAnalyzer.EXT_BUILD_PROCESS_GRAPH,
                (ProcessGraphAnalyzer analyzer) -> analyzer.buildProcessGraph(flowModel)
        );

        validateProcessGraph(processGraph);

        NodeGeneratorProvider nodeGeneratorProvider = createNodeGeneratorProvider();
        GeneratorContext context = new GeneratorContext(flowModel, processGraph, nodeGeneratorProvider);
        nodeGeneratorProvider.registerGenerator(flowModel, context);

        if (isStateful()) {
            classTarget.addSuperInterface(ClassWrapper.of(StatefulProcessInstance.class));
        } else {
            classTarget.addSuperInterface(ClassWrapper.of(ProcessInstance.class));
        }

        generateExecuteMethod(nodeGeneratorProvider);

        if (isStateful()) {
            generateTriggerMethods(context);
        }

        generateWrapResultMethod();

        final String javaCode = classTarget.generateCode();
        return safeFormat(javaCode);
    }

    @Override
    public String getClassFullName() {
        return classTarget.getFullName();
    }

    /**
     * An extension point for subclasses to add specific imported types required
     * by their process dialect (e.g., BpmnModel-specific classes).
     *
     * @param classTarget The target class to which imports should be added.
     */
    protected abstract void addImportedType(ClassTarget classTarget);

    protected void addImportedType(Class<?> clazz) {
        classTarget.addImportedType(ClassWrapper.of(clazz));
    }

    /**
     * Adds a set of default imports that are common to all generated process classes.
     */
    private void addDefaultImports() {
        addImportedType(Map.class);
        addImportedType(HashMap.class);
        addImportedType(Collections.class);
        addImportedType(ClassUtils.class);
        addImportedType(DataType.class);
        addImportedType(BeanProvider.class);
        addImportedType(ProcessSource.class);
        addImportedType(ProcessEngineFactory.class);
        addImportedType(EngineExecutionContextHolder.class);

        if (isStateful()) {
            addImportedType(StatefulProcessInstance.class);
        } else {
            addImportedType(ProcessInstance.class);
        }

        addImportedType(ParallelBranch.class);
        addImportedType(ParallelExecutor.class);
        addImportedType(ActionExecutor.class);
        addImportedType(GatewayExecutor.class);
        addImportedType(DynamicClassExecutor.class);
        addImportedType(ExecutionPolicy.class);
        addImportedType(RetryPolicies.class);
        addImportedType(RetryPolicyResolver.class);
        addImportedType(FailureHandlers.class);
        addImportedType(FailureHandlerResolver.class);
    }

    /**
     * Generates the main {@code execute()} method of the process class.
     * The body of this method is generated by the root node's generator.
     *
     * @param nodeGeneratorProvider The provider for finding node-specific generators.
     */
    private void generateExecuteMethod(NodeGeneratorProvider nodeGeneratorProvider) {
        generateFlowMethod(
                MethodConstants.EXECUTE_METHOD_NAME,
                Collections.singletonList(ParamTarget.of(ClassWrapper.of("Map<String, Object>"), CONTEXT_VAR_NAME)),
                code -> nodeGeneratorProvider.getGenerator(flowModel).generateCode(code)
        );
    }

    /**
     * A generic method for generating a standard flow-related method (like execute or trigger).
     * It handles parameter setup, context-to-variable mapping, and the return statement.
     *
     * @param methodName The name of the method to generate.
     * @param params     The parameters of the method.
     * @param body       A {@link CodeGenerator} that will produce the body of the method.
     */
    private void generateFlowMethod(String methodName, List<ParamTarget> params, CodeGenerator body) {
        MethodTarget method = createBaseMethodTarget(methodName, params);

        List<IVar> paramVars = flowModel.getParamVars();
        generateContextToVarsMapping(method, paramVars);
        method.addNewLine();

        body.generateCode(method);
        method.addNewLine();

        method.addBodyLine("return " + WRAP_RESULT_METHOD_NAME + "();");
        classTarget.addMethod(method);
    }

    /**
     * Generates the {@code trigger()} and event-specific {@code trigger(event)}
     * methods for stateful processes.
     *
     * @param context The generator context.
     */
    private void generateTriggerMethods(GeneratorContext context) {
        List<ParamTarget> triggerParams = Arrays.asList(
                ParamTarget.of(ClassWrapper.of("String"), "tag"),
                ParamTarget.of(ClassWrapper.of("Map<String, Object>"), CONTEXT_VAR_NAME)
        );
        MethodTarget trigger = createBaseMethodTarget(MethodConstants.TRIGGER_METHOD_NAME, triggerParams);
        new TriggerMethodGenerator().generateCode(trigger);
        classTarget.addMethod(trigger);

        List<ParamTarget> eventTriggerParams = Arrays.asList(
                ParamTarget.of(ClassWrapper.of("String"), "tag"),
                ParamTarget.of(ClassWrapper.of("String"), "event"),
                ParamTarget.of(ClassWrapper.of("Map<String, Object>"), CONTEXT_VAR_NAME)
        );
        generateFlowMethod(MethodConstants.TRIGGER_METHOD_NAME, eventTriggerParams,
                new StatefulTriggerMethodCodeGenerator(context));
    }

    /**
     * Generates the private helper method {@code _wrapResult()}, which is responsible
     * for collecting all output variables into a Map to be returned to the caller.
     */
    private void generateWrapResultMethod() {
        MethodTarget method = createBaseMethodTarget(WRAP_RESULT_METHOD_NAME, Collections.emptyList());

        List<IVar> returnVars = flowModel.getReturnVars();
        if (CollectionUtils.isEmpty(returnVars)) {
            method.addBodyLine("return java.util.Collections.emptyMap();");
            classTarget.addMethod(method);
            return;
        }

        method.addBodyLine("java.util.Map<String, Object> " + RESULT_VAR_NAME + " = new java.util.HashMap<>();");
        for (IVar v : returnVars) {
            method.addBodyLine(RESULT_VAR_NAME + ".put(\"" + v.getName() + "\", " + v.getName() + ");");
        }
        method.addBodyLine("return " + RESULT_VAR_NAME + ";");
        classTarget.addMethod(method);
    }

    /**
     * Creates a {@link MethodTarget} with a standard set of modifiers, return type,
     * and exceptions for use in all generated flow methods.
     *
     * @param name   The name of the method.
     * @param params The list of parameters for the method.
     * @return A new, pre-configured {@link MethodTarget}.
     */
    private MethodTarget createBaseMethodTarget(String name, List<ParamTarget> params) {
        MethodTarget m = new MethodTarget();
        m.setClassTarget(classTarget);
        m.setName(name);
        m.addModifier(Modifier.PUBLIC);
        m.setReturnType(ClassWrapper.of("Map<String, Object>"));
        m.addException(ClassWrapper.of(Exception.class));
        if (CollectionUtils.isNotEmpty(params)) {
            for (ParamTarget p : params) {
                m.addParameter(p);
            }
        }
        return m;
    }

    /**
     * Generates class fields for a given list of process variables.
     *
     * @param vars The list of variables to create fields for.
     */
    private void addClassFieldsForVars(List<IVar> vars) {
        if (CollectionUtils.isEmpty(vars)) {
            return;
        }
        for (IVar var : vars) {
            ClassWrapper type = ClassWrapper.of(var.getDataType());
            classTarget.addImportedType(type);
            String def = DataType.getDefaultValueString(
                    DataType.getJavaClass(var.getDataType()),
                    var.getDefaultValue()
            );
            classTarget.addField(type, var.getName(), def);
        }
    }

    /**
     * Generates the code that maps incoming context map values to the strongly-typed
     * process variable fields at the beginning of a method.
     *
     * @param methodTarget The method to add the mapping code to.
     * @param vars         The variables to map.
     */
    private void generateContextToVarsMapping(MethodTarget methodTarget, List<IVar> vars) {
        if (CollectionUtils.isEmpty(vars)) {
            return;
        }
        for (IVar paramVar : vars) {
            String contextGet = CONTEXT_VAR_NAME + ".get(\"" + paramVar.getName() + "\")";
            Class<?> javaType = DataType.getJavaClass(paramVar.getDataType());

            if (javaType.isPrimitive()) {
                String wrapperSimple = DataType.getSimplifiedClassName(DataType.getWrapperClass(javaType));
                String transferCall = "(" + wrapperSimple + ")DataType.transfer(" + contextGet + ", " + wrapperSimple + ".class)";
                String line = paramVar.getName() + " = " + transferCall + "." + DataType.getUnboxingMethodName(javaType) + ";";
                methodTarget.addBodyLine(line);
            } else {
                ClassWrapper pvType = ClassWrapper.of(paramVar.getDataType());
                String typeName = pvType.getShortRawName();
                String line = paramVar.getName() + " = (" + typeName + ")DataType.transfer(" + contextGet + ", " + typeName + ".class);";
                methodTarget.addBodyLine(line);
            }
        }
    }


    /**
     * Initializes the {@link ClassTarget} with its package, name, modifiers,
     * imports, and fields.
     */
    private void initClassTarget() {
        String full = FlowClassUtils.getFlowClassFullName(flowModel.getCode());
        ClassWrapper wrapper = ClassWrapper.of(full);
        classTarget.addModifier(Modifier.PUBLIC);
        classTarget.setPackageName(wrapper.getPackageName());
        classTarget.setFullName(wrapper.getName());
        classTarget.setName(wrapper.getShortName());

        addDefaultImports();
        addImportedType(classTarget);

        addClassFieldsForVars(flowModel.getParamVars());
        addClassFieldsForVars(flowModel.getReturnVars());
        addClassFieldsForVars(flowModel.getInnerVars());
    }

    /**
     * Determines if the process is stateful, which is defined by the presence
     * of at least one wait-capable node (e.g., a receive task).
     */
    public boolean isStateful() {
        return flowModel.getAllNodes().stream().anyMatch(n -> n instanceof StatefulElement);
    }

    /**
     * An extension point for subclasses to provide a dialect-specific
     * {@link NodeGeneratorProvider}.
     */
    protected abstract NodeGeneratorProvider createNodeGeneratorProvider();

    /**
     * Resets the internal {@link ClassTarget} to a clean state. This is called
     * at the beginning of each code generation run.
     */
    private void resetClassTarget() {
        this.classTarget = new ClassTarget();
        initClassTarget();
    }


    /**
     * Formats the generated Java code using the Google Java Formatter.
     * If formatting fails, it logs an error and returns the unformatted code.
     */
    private String safeFormat(String javaCode) {
        try {
            return FORMATTER.formatSourceAndFixImports(javaCode);
        } catch (NoSuchMethodError e) {
            try {
                return FORMATTER.formatSource(javaCode);
            } catch (FormatterException fe) {
                LOGGER.error("formatSource failed (len={}): {}", javaCode.length(), fe.getMessage(), fe);
                LOGGER.debug("Unformatted Java code:\n{}", javaCode);
                return javaCode;
            }
        } catch (FormatterException fe) {
            LOGGER.error("formatSourceAndFixImports failed (len={}): {}", javaCode.length(), fe.getMessage(), fe);
            LOGGER.debug("Unformatted Java code:\n{}", javaCode);
            return javaCode;
        }
    }


    /**
     * Validate that the generated ProcessGraph contains expected entries for gateways,
     * reducing surprises during codegen. This is a conservative check and can be relaxed
     * in future if needed.
     */
    private void validateProcessGraph(ProcessGraph processGraph) {
        try {
            Map<String, List<TransitionNode>> followingGraph = processGraph.getFollowingGraph();
            Map<String, List<TransitionNode>> branchGraph = processGraph.getBranchGraph();

            // Iterate over all nodes, focus on gateways
            List<TransitionNode> allNodes = flowModel.getAllNodes();
            for (TransitionNode node : allNodes) {
                if (node instanceof GatewayElement) {
                    // followingGraph should at least contain a key for the gateway id
                    if (!followingGraph.containsKey(node.getId())) {
                        throw new CompileFlowException.BusinessException(
                                ErrorCode.CF_VALIDATION_005,
                                "Invalid process graph: followingGraph missing key for gateway '"
                                        + node.getId() + "' in flow '" + flowModel.getCode() + "'.",
                                null
                        );
                    }

                    // For each outgoing as a branch start, branchGraph should contain a key
                    List<TransitionNode> outgoing = node.getOutgoingNodes();
                    if (CollectionUtils.isNotEmpty(outgoing)) {
                        for (TransitionNode branchStart : outgoing) {
                            String key = ProcessUtils.buildBranchKey(node, branchStart);
                            if (!branchGraph.containsKey(key)) {
                                throw new CompileFlowException.BusinessException(
                                        ErrorCode.CF_VALIDATION_005,
                                        "Invalid process graph: branchGraph missing key '" + key
                                                + "' for gateway '" + node.getId() + "' in flow '" + flowModel.getCode() + "'.",
                                        null
                                );
                            }
                        }
                    }
                }
            }
        } catch (CompileFlowException e) {
            throw e;
        } catch (Throwable t) {
            throw new CompileFlowException.BusinessException(
                    ErrorCode.CF_VALIDATION_005,
                    "Process graph validation failed for flow '" + flowModel.getCode() + "'",
                    t
            );
        }
    }

}
