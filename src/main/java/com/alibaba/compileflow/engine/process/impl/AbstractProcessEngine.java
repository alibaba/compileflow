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
package com.alibaba.compileflow.engine.process.impl;

import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.DirectedGraph;
import com.alibaba.compileflow.engine.common.constants.FlowModelType;
import com.alibaba.compileflow.engine.common.util.ArrayUtils;
import com.alibaba.compileflow.engine.common.util.MapUtils;
import com.alibaba.compileflow.engine.definition.common.EndElement;
import com.alibaba.compileflow.engine.definition.common.FlowModel;
import com.alibaba.compileflow.engine.definition.common.TransitionNode;
import com.alibaba.compileflow.engine.definition.common.TransitionSupport;
import com.alibaba.compileflow.engine.extension.ExtensionInvoker;
import com.alibaba.compileflow.engine.extension.filter.ReduceFilter;
import com.alibaba.compileflow.engine.process.builder.converter.FlowModelConverter;
import com.alibaba.compileflow.engine.process.builder.converter.impl.parser.model.FlowStreamSource;
import com.alibaba.compileflow.engine.process.builder.loader.FlowSourceLoader;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;
import org.apache.commons.collections4.CollectionUtils;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author wuxiang
 * @author yusu
 */
public abstract class AbstractProcessEngine<T extends FlowModel<? extends TransitionNode>> implements ProcessEngine<T> {

    protected final Map<String, AbstractProcessRuntime> runtimeCache = new ConcurrentHashMap<>();

    private boolean concurrentCompilation;
    private ExecutorService compilationExecutorService;
    private static final ExecutorService SHARED_COMPILATION_EXECUTOR = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            new CompileFlowThreadFactory());
    private ClassLoader classLoader;

    /**
     * Sets a default ClassLoader for this process engine instance.
     * This ClassLoader will be used for flow compilation and class loading
     * unless a different one is provided in a specific method call.
     *
     * @param classLoader The default class loader to use.
     */
    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    static class CompileFlowThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix = "compileflow-shared-pool-thread-";

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }

    public void setConcurrentCompilation(boolean concurrentCompilation) {
        this.concurrentCompilation = concurrentCompilation;
    }

    public void setCompilationExecutorService(ExecutorService compilationExecutorService) {
        this.compilationExecutorService = compilationExecutorService;
    }

    @Override
    public void preCompile(String... codes) {
        if (ArrayUtils.isEmpty(codes)) {
            throw new CompileFlowException("No process to compile");
        }

        if (this.concurrentCompilation) {
            executeInParallel(Arrays.stream(codes), code -> preCompile(code));
        } else {
            for (String code : codes) {
                preCompile(code);
            }
        }
    }

    @Override
    public void preCompile(Map<String, String> code2ContentMap) {
        if (MapUtils.isEmpty(code2ContentMap)) {
            return;
        }

        if (this.concurrentCompilation) {
            executeInParallel(code2ContentMap.entrySet().stream(),
                    entry -> preCompile(entry.getKey(), entry.getValue()));
        } else {
            code2ContentMap.forEach((code, content) -> preCompile(code, content));
        }
    }

    @Override
    public void reCompile(String... codes) {
        reCompile(this.classLoader, codes);
    }

    @Override
    public void reCompile(ClassLoader classLoader, String... codes) {
        if (ArrayUtils.isEmpty(codes)) {
            throw new CompileFlowException("No process to re-compile");
        }

        if (this.concurrentCompilation) {
            executeInParallel(Arrays.stream(codes), code -> reCompile(classLoader, code));
        } else {
            for (String code : codes) {
                reCompile(classLoader, code);
            }
        }
    }

    @Override
    public void reCompile(ClassLoader classLoader, Map<String, String> code2ContentMap) {
        if (MapUtils.isEmpty(code2ContentMap)) {
            return;
        }

        if (this.concurrentCompilation) {
            executeInParallel(code2ContentMap.entrySet().stream(),
                    entry -> reCompile(entry.getKey(), entry.getValue()));
        } else {
            code2ContentMap.forEach((code, content) -> reCompile(code, content));
        }
    }

    @Override
    public void reCompile(Map<String, String> code2ContentMap) {
        reCompile(null, code2ContentMap);
    }

    /**
     * Executes a given action on each element of a stream in parallel.
     * This method centralizes the logic for choosing between a custom ExecutorService
     * and the default ExecutorService, as well as handling exceptions.
     *
     * @param stream The stream of items to process.
     * @param action The action to perform on each item.
     * @param <E>    The type of elements in the stream.
     */
    private <E> void executeInParallel(Stream<E> stream, Consumer<E> action) {
        ExecutorService executor = (this.compilationExecutorService != null) ?
                this.compilationExecutorService : SHARED_COMPILATION_EXECUTOR;
        try {
            List<CompletableFuture<Void>> futures = stream
                    .map(item -> CompletableFuture.runAsync(() -> action.accept(item), executor))
                    .collect(Collectors.toList());
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (CompletionException e) {
            throw new CompileFlowException(e);
        }
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

    private void preCompile(String code) {
        preCompile(code, null);
    }

    private void preCompile(String code, String content) {
        getProcessRuntime(code, content);
    }

    private void reCompile(ClassLoader classLoader, String code) {
        reCompile(classLoader, code, null);
    }

    private void reCompile(ClassLoader classLoader, String code, String content) {
        String cacheKey = getCacheKey(code);
        runtimeCache.compute(cacheKey, (key, oldRuntime) -> {
            AbstractProcessRuntime newRuntime = getRuntimeFromSource(key, content);
            newRuntime.recompile(classLoader);
            return newRuntime;
        });
    }

    private AbstractProcessRuntime getCompiledRuntime(String code, String content) {
        AbstractProcessRuntime runtime = getRuntimeFromSource(code, content);
        runtime.compile(this.classLoader);
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

    @Override
    public T load(String code, String content) {
        FlowStreamSource flowStreamSource = ExtensionInvoker.getInstance().invoke(FlowSourceLoader.EXT_LOAD_FLOW_SOURCE_CODE,
                ReduceFilter.first(), code, content, getFlowModelType());

        T flowModel = (T) getFlowModelConverter().convertToModel(flowStreamSource);
        if (flowModel == null) {
            throw new RuntimeException("No valid flow model found, code is " + code);
        }

        validateFlowModel(flowModel);
        sortTransition(flowModel);

        return flowModel;
    }

    private void validateFlowModel(T flowModel) {
        checkCycle(flowModel);
        checkContinuous(flowModel);
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
    public String getTestJavaCode(String code) {
        return getTestJavaCode(code, null);
    }

    @Override
    public String getTestJavaCode(String code, String content) {
        AbstractProcessRuntime runtime = getRuntimeFromSource(code, content);
        return runtime.generateTestJavaCode();
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
        DirectedGraph<TransitionNode> directedGraph = buildDirectedGraph(flowModel);
        List<TransitionNode> cyclicVertexList = directedGraph.findCyclicVertexList();
        if (CollectionUtils.isNotEmpty(cyclicVertexList)) {
            throw new CompileFlowException("Cyclic nodes found in flow " + flowModel.getCode()
                    + " check node [" + cyclicVertexList.stream().map(TransitionNode::getId)
                    .collect(Collectors.joining(",")) + "]");
        }
    }

    private DirectedGraph<TransitionNode> buildDirectedGraph(T flowModel) {
        DirectedGraph<TransitionNode> directedGraph = new DirectedGraph<>();
        for (TransitionNode node : flowModel.getAllNodes()) {
            List<TransitionNode> outgoingNodes = node.getOutgoingNodes();
            if (CollectionUtils.isNotEmpty(outgoingNodes)) {
                outgoingNodes.forEach(outgoingNode -> directedGraph.add(DirectedGraph.Edge.of(node, outgoingNode)));
            }
        }
        return directedGraph;
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
