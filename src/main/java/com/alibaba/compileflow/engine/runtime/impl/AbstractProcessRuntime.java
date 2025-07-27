/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.runtime.impl;

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.Lifecycle;
import com.alibaba.compileflow.engine.common.constants.FlowModelType;
import com.alibaba.compileflow.engine.definition.common.FlowModel;
import com.alibaba.compileflow.engine.definition.common.Node;
import com.alibaba.compileflow.engine.definition.common.TransitionNode;
import com.alibaba.compileflow.engine.definition.common.var.IVar;
import com.alibaba.compileflow.engine.definition.tbbpm.WaitEventTaskNode;
import com.alibaba.compileflow.engine.definition.tbbpm.WaitTaskNode;
import com.alibaba.compileflow.engine.extension.ExtensionInvoker;
import com.alibaba.compileflow.engine.extension.filter.ReduceFilter;
import com.alibaba.compileflow.engine.process.builder.generator.provider.NodeGeneratorProvider;
import com.alibaba.compileflow.engine.process.builder.validator.FlowModelValidator;
import com.alibaba.compileflow.engine.process.builder.validator.ValidateMessage;
import com.alibaba.compileflow.engine.process.builder.validator.factory.ModelValidatorFactory;
import com.alibaba.compileflow.engine.runtime.ProcessCodeGenerator;
import com.alibaba.compileflow.engine.runtime.ProcessRuntime;
import com.alibaba.compileflow.engine.runtime.graph.ProcessGraph;
import com.alibaba.compileflow.engine.runtime.graph.ProcessGraphAnalyzer;
import com.alibaba.compileflow.engine.runtime.instance.ProcessInstance;
import com.alibaba.compileflow.engine.runtime.instance.StatefulProcessInstance;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author wuxiang
 * @author yusu
 */
public abstract class AbstractProcessRuntime<T extends FlowModel> implements ProcessRuntime, Lifecycle {

    private static final ProcessCompiler compiler = new ProcessCompiler();
    private static final ProcessExecutor executor = new ProcessExecutor();
    protected ProcessCodeGenerator processCodeGenerator;

    protected T flowModel;
    protected final String code;

    protected final String id;
    private final String name;
    private final List<IVar> vars;
    private ProcessGraph processGraph;

    @SuppressWarnings("unchecked")
    public AbstractProcessRuntime(T flowModel) {
        if (flowModel == null) {
            throw new IllegalArgumentException("flowModel is null");
        }
        this.flowModel = flowModel;
        this.id = flowModel.getId();
        this.code = flowModel.getCode();
        this.name = flowModel.getName();
        this.vars = flowModel.getVars();
        this.processCodeGenerator = getProcessCodeGenerator();
    }

    @Override
    public Map<String, Object> start(Map<String, Object> context) {
        Class<? extends ProcessInstance> compiledClass = getOrCompile(null);
        return executor.execute(code, compiledClass, context);
    }

    @Override
    public Map<String, Object> trigger(String tag, Map<String, Object> context) {
        Class<? extends StatefulProcessInstance> compiledClass = getOrCompile(null);
        return executor.trigger(code, compiledClass, tag, context);
    }

    @Override
    public Map<String, Object> trigger(String tag, String event, Map<String, Object> context) {
        Class<? extends StatefulProcessInstance> compiledClass = getOrCompile(null);
        return executor.trigger(code, compiledClass, tag, event, context);
    }

    @Override
    public void init() {
        validateFlowModel();
        initGatewayGraph();
    }

    @Override
    public void stop() {
        // No-op for now
    }

    @Override
    public void compile() {
        getOrCompile(null);
    }

    @Override
    public void compile(ClassLoader classLoader) {
        getOrCompile(classLoader);
    }

    @Override
    public void recompile() {
        recompile(null);
    }

    @Override
    public void recompile(ClassLoader classLoader) {
        ProcessCodeGenerator codeGenerator = getProcessCodeGenerator();
        String javaSource = codeGenerator.regenerateCode();
        String fullClassName = codeGenerator.getClassFullName();
        compiler.recompile(code, fullClassName, javaSource, classLoader);
    }

    protected abstract ProcessCodeGenerator getProcessCodeGenerator();

    public T getFlowModel() {
        return flowModel;
    }

    public String getName() {
        return name;
    }

    public List<IVar> getVars() {
        return vars;
    }

    public Map<String, List<TransitionNode>> getFollowingGraph() {
        return processGraph.getFollowingGraph();
    }

    public Map<String, List<TransitionNode>> getBranchGraph() {
        return processGraph.getBranchGraph();
    }

    @SuppressWarnings("unchecked")
    public <P extends NodeGeneratorProvider> P getNodeGeneratorProvider() {
        return (P) processCodeGenerator.getNodeGeneratorProvider();
    }

    public Node getNodeById(String id) {
        return flowModel.getNode(id);
    }

    public abstract FlowModelType getFlowModelType();

    protected abstract boolean isBpmn20();

    protected boolean isStateful() {
        return flowModel.getAllNodes().stream()
                .anyMatch(node -> node instanceof WaitTaskNode || node instanceof WaitEventTaskNode);
    }

    public String generateJavaCode() {
        return processCodeGenerator.generateCode();
    }

    public String generateTestJavaCode() {
        return processCodeGenerator.generateTestCode();
    }


    @SuppressWarnings("unchecked")
    private <I extends ProcessInstance> Class<I> getOrCompile(ClassLoader classLoader) {
        Class<?> clazz = compiler.getCompiledClass(code);
        if (clazz == null) {
            String javaCode = generateJavaCode();
            String fullClassName = processCodeGenerator.getClassFullName();
            clazz = compiler.getOrCompile(code, fullClassName, javaCode, classLoader);
        }
        if (clazz == null) {
            throw new CompileFlowException("Failed to compile or get compiled class for process code: " + code);
        }
        return (Class<I>) clazz;
    }

    private void initGatewayGraph() {
        processGraph = ExtensionInvoker.getInstance().invoke(ProcessGraphAnalyzer.EXT_BUILD_PROCESS_GRAPH,
                ReduceFilter.first(), flowModel);
    }

    @SuppressWarnings("unchecked")
    protected void validateFlowModel() {
        FlowModelValidator validator = ModelValidatorFactory.getFlowModelValidator(getFlowModelType());
        List<ValidateMessage> validateMessages = validator.validate(flowModel);
        if (validateMessages == null) {
            validateMessages = new ArrayList<>();
        }
        List<TransitionNode> transitionNodes = flowModel.getTransitionNodes();
        if (isStateful()) {
            List<TransitionNode> noTagNodes = transitionNodes.stream().filter(node -> StringUtils.isEmpty(node.getTag()))
                    .collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(noTagNodes)) {
                String noTagNodeIds = noTagNodes.stream().map(Node::getId).collect(Collectors.joining(","));
                validateMessages.add(ValidateMessage.fail("Nodes [" + noTagNodeIds + "] have no Tag"));
            }
        }

        List<ValidateMessage> failures = validateMessages.stream()
                .filter(ValidateMessage::isFail)
                .collect(Collectors.toList());

        if (!failures.isEmpty()) {
            throw new CompileFlowException("Flow model validation failed: " + failures);
        }
    }

}
