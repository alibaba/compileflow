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
package com.alibaba.compileflow.engine.process.impl;

import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.DirectedGraph;
import com.alibaba.compileflow.engine.common.constant.FlowModelType;
import com.alibaba.compileflow.engine.common.util.ArrayUtils;
import com.alibaba.compileflow.engine.definition.common.EndElement;
import com.alibaba.compileflow.engine.definition.common.FlowModel;
import com.alibaba.compileflow.engine.definition.common.TransitionNode;
import com.alibaba.compileflow.engine.definition.common.TransitionSupport;
import com.alibaba.compileflow.engine.process.preruntime.compiler.impl.FlowClassLoader;
import com.alibaba.compileflow.engine.process.preruntime.converter.FlowModelConverter;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.model.FlowStreamSource;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.model.ResourceFlowStreamSource;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author wuxiang
 * @author yusu
 */
public abstract class AbstractProcessEngine<T extends FlowModel<? extends TransitionNode>> implements ProcessEngine<T> {

    private final Map<String, AbstractProcessRuntime> runtimeCache = new ConcurrentHashMap<>();

    @Override
    public void preCompile(String... codes) {
        preCompile(null, codes);
    }

    @Override
    public void preCompile(ClassLoader classLoader, String... codes) {
        if (ArrayUtils.isEmpty(codes)) {
            throw new CompileFlowException("No process to compile");
        }

        for (String code : codes) {
            AbstractProcessRuntime runtime = getProcessRuntime(code);
            runtime.compile(classLoader);
        }
    }

    @Override
    public void reload(String code) {
        FlowClassLoader.getInstance().clearCache();
        AbstractProcessRuntime runtime = runtimeCache.computeIfPresent(code, (k, v) -> getRuntimeFromSource(code));
        runtime.recompile(code);
    }

    @SuppressWarnings("unchecked")
    protected <R extends AbstractProcessRuntime> R getProcessRuntime(String code) {
        String cacheKey = getCacheKey(code);
        AbstractProcessRuntime runtime = runtimeCache.computeIfAbsent(cacheKey, c ->
            getCompiledRuntime(code));
        return (R) runtime;
    }

    private AbstractProcessRuntime getCompiledRuntime(String code) {
        AbstractProcessRuntime runtime = getRuntimeFromSource(code);
        runtime.compile();
        return runtime;
    }

    private AbstractProcessRuntime getRuntimeFromSource(String code) {
        T flowModel = load(code);
        AbstractProcessRuntime runtime = getRuntimeFromModel(flowModel);
        runtime.init();
        return runtime;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T load(String code) {
        FlowStreamSource flowStreamSource = loadFlowSource(code);

        T flowModel = (T) getFlowModelConverter().convertToModel(flowStreamSource);
        if (flowModel == null) {
            throw new RuntimeException("No valid flow model found, code is " + code);
        }

        checkCycle(flowModel);
        checkContinuous(flowModel);
        sortTransition(flowModel);

        return flowModel;
    }

    @Override
    public String getJavaCode(String code) {
        AbstractProcessRuntime runtime = getRuntimeFromSource(code);
        return runtime.generateJavaCode();
    }

    @Override
    public String getTestCode(String code) {
        AbstractProcessRuntime runtime = getRuntimeFromSource(code);
        return runtime.generateTestCode();
    }

    private FlowStreamSource loadFlowSource(String code) {
        String filePath = convertToFilePath(code);
        return ResourceFlowStreamSource.of(filePath);
    }

    private String convertToFilePath(String code) {
        String path = code.replace(".", "/");
        FlowModelType flowModelType = getFlowModelType();
        return path + getBpmFileSuffix(flowModelType);
    }

    private String getBpmFileSuffix(FlowModelType flowModelType) {
        if (FlowModelType.BPMN.equals(flowModelType)) {
            return ".bpmn20";
        }
        return ".bpm";
    }

    private void checkContinuous(T flowModel) {
        ArrayList<TransitionNode> visitedNodes = new ArrayList<>();
        checkContinuous(flowModel.getStartNode(), visitedNodes, flowModel);
    }

    private void checkContinuous(TransitionNode node, List<TransitionNode> visitedNodes, FlowModel flowModel) {
        visitedNodes.add(node);
        if (node instanceof EndElement) {
            return;
        }
        List<TransitionNode> outgoingNodes = node.getOutgoingNodes();
        if (CollectionUtils.isEmpty(outgoingNodes)) {
            throw new CompileFlowException("Flow should end with an end node " + flowModel);
        }

        for (TransitionNode outgoingNode : outgoingNodes) {
            if (!visitedNodes.contains(outgoingNode)) {
                checkContinuous(outgoingNode, visitedNodes, flowModel);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void checkCycle(T flowModel) {
        DirectedGraph<TransitionNode> directedGraph = new DirectedGraph<>();
        for (TransitionNode node : flowModel.getAllNodes()) {
            List<TransitionNode> outgoingNodes = node.getOutgoingNodes();
            if (CollectionUtils.isNotEmpty(outgoingNodes)) {
                outgoingNodes.forEach(
                    outgoingNode -> directedGraph.add(DirectedGraph.Edge.of(node, outgoingNode)));
            }
        }
        List<TransitionNode> cyclicVertexList = directedGraph.findCyclicVertexList();
        if (CollectionUtils.isNotEmpty(cyclicVertexList)) {
            throw new CompileFlowException("Cyclic nodes found in flow " + flowModel.getCode()
                + " check node [" + cyclicVertexList.stream().map(TransitionNode::getId)
                .collect(Collectors.joining(",")) + "]");
        }
    }

    private void sortTransition(T flowModel) {
        flowModel.getAllNodes().forEach(node -> node.getTransitions()
            .sort(Comparator.comparing(TransitionSupport::getPriority).reversed()));
    }

    private String getCacheKey(String code) {
        return code;
    }

    protected abstract FlowModelType getFlowModelType();

    protected abstract FlowModelConverter getFlowModelConverter();

    protected abstract AbstractProcessRuntime getRuntimeFromModel(T flowModel);

}
