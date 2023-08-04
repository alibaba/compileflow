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
import com.alibaba.compileflow.engine.common.extension.ExtensionInvoker;
import com.alibaba.compileflow.engine.common.extension.filter.ReduceFilter;
import com.alibaba.compileflow.engine.common.util.ArrayUtils;
import com.alibaba.compileflow.engine.common.util.MapUtils;
import com.alibaba.compileflow.engine.definition.common.EndElement;
import com.alibaba.compileflow.engine.definition.common.FlowModel;
import com.alibaba.compileflow.engine.definition.common.TransitionNode;
import com.alibaba.compileflow.engine.definition.common.TransitionSupport;
import com.alibaba.compileflow.engine.process.preruntime.converter.FlowModelConverter;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.model.FlowStreamSource;
import com.alibaba.compileflow.engine.process.preruntime.loader.FlowSourceLoader;
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
            preCompile(classLoader, code);
        }
    }

    @Override
    public void preCompile(Map<String, String> code2ContentMap) {
        preCompile(null, code2ContentMap);
    }

    @Override
    public void preCompile(ClassLoader classLoader, Map<String, String> code2ContentMap) {
        if (MapUtils.isEmpty(code2ContentMap)) {
            return;
        }
        code2ContentMap.forEach((code, content) -> preCompile(classLoader, code, content));
    }

    protected <R extends AbstractProcessRuntime> R getProcessRuntime(String code) {
        return getProcessRuntime(code, null);
    }

    protected <R extends AbstractProcessRuntime> R getProcessRuntime(String code, String content) {
        String cacheKey = getCacheKey(code);
        AbstractProcessRuntime runtime = runtimeCache.computeIfAbsent(cacheKey, c ->
                getCompiledRuntime(code, content));
        return (R) runtime;
    }

    private void preCompile(ClassLoader classLoader, String code) {
        preCompile(classLoader, code, null);
    }

    private void preCompile(ClassLoader classLoader, String code, String content) {
        AbstractProcessRuntime runtime = getProcessRuntime(code, content);
        runtime.compile(classLoader);
    }

    private AbstractProcessRuntime getCompiledRuntime(String code, String content) {
        AbstractProcessRuntime runtime = getRuntimeFromSource(code, content);
        runtime.compile();
        return runtime;
    }

    private AbstractProcessRuntime getRuntimeFromSource(String code, String content) {
        T flowModel = load(code, content);
        AbstractProcessRuntime runtime = getRuntimeFromModel(flowModel);
        runtime.init();
        return runtime;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T load(String code) {
        return load(code, null);
    }

    public T load(String code, String content) {
        FlowStreamSource flowStreamSource = ExtensionInvoker.getInstance().invoke(FlowSourceLoader.EXT_LOAD_FLOW_SOURCE_CODE,
                ReduceFilter.first(), code, content, getFlowModelType());

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
        return getJavaCode(code, null);
    }

    @Override
    public String getJavaCode(String code, String content) {
        AbstractProcessRuntime runtime = getRuntimeFromSource(code, content);
        return runtime.generateJavaCode();
    }

    @Override
    public String getTestCode(String code) {
        return getTestCode(code, null);
    }

    @Override
    public String getTestCode(String code, String content) {
        AbstractProcessRuntime runtime = getRuntimeFromSource(code, content);
        return runtime.generateTestCode();
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
