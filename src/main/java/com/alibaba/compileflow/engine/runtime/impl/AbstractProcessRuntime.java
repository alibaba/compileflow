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
package com.alibaba.compileflow.engine.runtime.impl;

import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.common.ClassWrapper;
import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.Lifecycle;
import com.alibaba.compileflow.engine.common.constant.FlowModelType;
import com.alibaba.compileflow.engine.common.util.ClassUtils;
import com.alibaba.compileflow.engine.common.util.DataType;
import com.alibaba.compileflow.engine.common.util.ObjectFactory;
import com.alibaba.compileflow.engine.common.util.VarUtils;
import com.alibaba.compileflow.engine.definition.common.*;
import com.alibaba.compileflow.engine.definition.common.var.IVar;
import com.alibaba.compileflow.engine.definition.tbbpm.EventNode;
import com.alibaba.compileflow.engine.definition.tbbpm.WaitEventTaskNode;
import com.alibaba.compileflow.engine.definition.tbbpm.WaitTaskNode;
import com.alibaba.compileflow.engine.process.preruntime.compiler.Compiler;
import com.alibaba.compileflow.engine.process.preruntime.compiler.impl.CompilerImpl;
import com.alibaba.compileflow.engine.process.preruntime.generator.Generator;
import com.alibaba.compileflow.engine.process.preruntime.generator.bean.BeanProvider;
import com.alibaba.compileflow.engine.process.preruntime.generator.bean.SpringApplicationContextProvider;
import com.alibaba.compileflow.engine.process.preruntime.generator.bean.SpringBeanHolder;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.ClassTarget;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.MethodTarget;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.ParamTarget;
import com.alibaba.compileflow.engine.process.preruntime.generator.constansts.MethodConstants;
import com.alibaba.compileflow.engine.process.preruntime.generator.constansts.Modifier;
import com.alibaba.compileflow.engine.process.preruntime.generator.constansts.SystemEventConstants;
import com.alibaba.compileflow.engine.process.preruntime.generator.factory.GeneratorFactory;
import com.alibaba.compileflow.engine.process.preruntime.generator.factory.GeneratorProviderFactory;
import com.alibaba.compileflow.engine.process.preruntime.generator.impl.EventTriggerMethodGenerator;
import com.alibaba.compileflow.engine.process.preruntime.generator.impl.TriggerMethodGenerator;
import com.alibaba.compileflow.engine.process.preruntime.generator.provider.NodeGeneratorProvider;
import com.alibaba.compileflow.engine.process.preruntime.generator.script.ScriptExecutor;
import com.alibaba.compileflow.engine.process.preruntime.generator.script.ScriptExecutorProvider;
import com.alibaba.compileflow.engine.process.preruntime.generator.script.impl.MvelExecutor;
import com.alibaba.compileflow.engine.process.preruntime.generator.script.impl.QLExecutor;
import com.alibaba.compileflow.engine.process.preruntime.validator.FlowModelValidator;
import com.alibaba.compileflow.engine.process.preruntime.validator.ValidateMessage;
import com.alibaba.compileflow.engine.process.preruntime.validator.factory.ModelValidatorFactory;
import com.alibaba.compileflow.engine.runtime.ProcessRuntime;
import com.alibaba.compileflow.engine.runtime.instance.ProcessInstance;
import com.alibaba.compileflow.engine.runtime.instance.StatefulProcessInstance;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * @author wuxiang
 * @author yusu
 */
public abstract class AbstractProcessRuntime<T extends FlowModel> implements ProcessRuntime, Lifecycle {

    private static final Compiler COMPILER = new CompilerImpl();
    private static final AtomicBoolean inited = new AtomicBoolean(false);
    protected final Map<String, List<TransitionNode>> followingGraph = new HashMap<>();
    protected final Map<String, List<TransitionNode>> branchGraph = new HashMap<>();
    private final Map<String, String> javaCodeCache = new ConcurrentHashMap<>();
    private final Map<String, Class<?>> compiledClassCache = new ConcurrentHashMap<>();
    protected T flowModel;
    protected ClassTarget classTarget;
    protected NodeGeneratorProvider nodeGeneratorProvider;
    protected String code;
    private final String id;
    private final String name;
    private List<IVar> vars;
    private final List<IVar> paramVars;
    private final List<IVar> returnVars;
    private final List<IVar> innerVars;

    @SuppressWarnings("unchecked")
    public AbstractProcessRuntime(T flowModel) {
        this.flowModel = flowModel;
        if (this.flowModel == null) {
            throw new IllegalArgumentException("flowModel is null");
        }
        this.id = flowModel.getId();
        this.code = flowModel.getCode();
        this.name = flowModel.getName();
        this.vars = flowModel.getVars();
        this.vars = flowModel.getVars();
        this.paramVars = flowModel.getParamVars();
        this.returnVars = flowModel.getReturnVars();
        this.innerVars = flowModel.getInnerVars();
        this.classTarget = new ClassTarget();
    }

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
        return followingGraph;
    }

    public Map<String, List<TransitionNode>> getBranchGraph() {
        return branchGraph;
    }

    public <P extends NodeGeneratorProvider> P getNodeGeneratorProvider() {
        return (P) nodeGeneratorProvider;
    }

    public void compile() {
        compiledClassCache.computeIfAbsent(code, c -> compileJavaCode(getJavaCode(code)));
    }

    public void compile(ClassLoader classLoader) {
        compiledClassCache.computeIfAbsent(code, c -> compileJavaCode(getJavaCode(code), classLoader));
    }

    public void recompile(String code) {
        compiledClassCache.computeIfPresent(code, (k, v) -> compileJavaCode(generateJavaCode()));
    }

    public Node getNodeById(String id) {
        return flowModel.getNode(id);
    }

    @Override
    public Map<String, Object> start(Map<String, Object> context) {
        compile();
        return executeProcessInstance(context);
    }

    @Override
    public Map<String, Object> trigger(String tag, Map<String, Object> context) {
        compile();
        return triggerProcessInstance(tag, context);
    }

    @Override
    public Map<String, Object> trigger(String tag, String event, Map<String, Object> context) {
        compile();
        return triggerProcessInstance(tag, event, context);
    }

    public abstract FlowModelType getFlowModelType();

    public String generateJavaCode() {
        boolean stateful = isStateful();
        if (stateful) {
            classTarget.addSuperInterface(ClassWrapper.of(StatefulProcessInstance.class));
            classTarget.addImportedType(ClassWrapper.of(SystemEventConstants.class));
        } else {
            classTarget.addSuperInterface(ClassWrapper.of(ProcessInstance.class));
        }
        generateFlowMethod(MethodConstants.EXECUTE_METHOD_NAME,
            Collections.singletonList(ParamTarget.of(ClassWrapper.of("Map<String, Object>"), "_pContext")),
            this::generateExecuteMethodBody, stateful);
        if (stateful) {
            List<ParamTarget> paramTargets = new ArrayList<>(2);
            paramTargets.add(ParamTarget.of(ClassWrapper.of("String"), "tag"));
            paramTargets.add(ParamTarget.of(ClassWrapper.of("Map<String, Object>"), "_pContext"));
            TriggerMethodGenerator triggerMethodGenerator = new TriggerMethodGenerator();
            MethodTarget methodTarget = generateMethodDefinition(MethodConstants.TRIGGER_METHOD_NAME, paramTargets);
            triggerMethodGenerator.generateCode(methodTarget);
            classTarget.addMethod(methodTarget);
            paramTargets = new ArrayList<>(3);
            paramTargets.add(ParamTarget.of(ClassWrapper.of("String"), "tag"));
            paramTargets.add(ParamTarget.of(ClassWrapper.of("String"), "event"));
            paramTargets.add(ParamTarget.of(ClassWrapper.of("Map<String, Object>"), "_pContext"));
            EventTriggerMethodGenerator eventTriggerMethodGenerator = new EventTriggerMethodGenerator(this);
            generateFlowMethod(MethodConstants.TRIGGER_METHOD_NAME, paramTargets, eventTriggerMethodGenerator, stateful);
        }
        return classTarget.generateCode();
    }

    @Override
    public void init() {
        validateRuntime();
        initClassTarget();
        initGeneratorProvider();
        if (inited.compareAndSet(false, true)) {
            initBeanProvider();
            initScriptExecutorProvider();
        }
        initGatewayGraph();
    }

    @Override
    public void stop() {

    }

    public String generateTestCode() {
        ClassTarget classTarget = new ClassTarget();
        String fullClassName = getFlowTestClassFullName(code, id);
        ClassWrapper classWrapper = ClassWrapper.of(fullClassName);
        classTarget.addModifier(Modifier.PUBLIC);
        classTarget.setPackageName(classWrapper.getPackageName());
        classTarget.setFullName(classWrapper.getName());
        classTarget.setName(classWrapper.getShortName());
        addJavaTypes();
        addTestImport(classTarget);
        classTarget.addModifier(Modifier.PUBLIC);
        classTarget.setSuperClass(ClassWrapper.of("junit.framework.TestCase"));
        MethodTarget method = new MethodTarget();
        method.addModifier(Modifier.PUBLIC);
        method.setName("testProcess");
        method.addAnnotation("@Test");
        method.addException(ClassWrapper.of(Exception.class));
        method.addBodyLine("String code = \"" + code + "\";");

        String engineCode = isBpmn20() ? "ProcessEngine<BpmnModel> engine = ProcessEngineFactory.getProcessEngine(FlowModelType.BPMN);"
            : "ProcessEngine engine = ProcessEngineFactory.getProcessEngine();";
        method.addBodyLine(engineCode);
        method.addBodyLine("System.out.println(engine.getJavaCode(code));");
        method.addBodyLine("Map<String, Object> context = new HashMap<String, Object>();");
        if (CollectionUtils.isNotEmpty(this.paramVars)) {
            for (IVar v : this.paramVars) {
                String nullValueStr = DataType.getDefaultValueString(DataType.getJavaClass(v.getDataType()),
                    v.getDefaultValue());
                method.addBodyLine("context.put(\"" + v.getName() + "\", " + nullValueStr + ");");
            }
        }
        method.addBodyLine("try {");
        method.addBodyLine("System.out.println(engine.execute(code, context));");
        method.addBodyLine("}");
        method.addBodyLine("catch (Exception e) {");
        method.addBodyLine("e.printStackTrace();");
        method.addBodyLine("Assert.fail(e.getMessage());");
        method.addBodyLine("}");
        classTarget.addMethod(method);
        return classTarget.generateCode();
    }

    public boolean hasEventNode() {
        return flowModel.getAllNodes().stream().anyMatch(e -> e instanceof EventNode);
    }

    protected abstract void registerNodeGenerator(NodeContainer<TransitionNode> nodeContainer);

    protected abstract boolean isBpmn20();

    private void addTestImport(ClassTarget classTarget) {
        classTarget.addImportedType(ClassWrapper.of("org.junit.Test"));
        classTarget.addImportedType(ClassWrapper.of(ProcessEngine.class));
        classTarget.addImportedType(ClassWrapper.of(ProcessEngineFactory.class));
        classTarget.addImportedType(ClassWrapper.of(Map.class));
        classTarget.addImportedType(ClassWrapper.of(HashMap.class.getName()));
        classTarget.addImportedType(ClassWrapper.of("junit.framework.TestCase"));
        classTarget.addImportedType(ClassWrapper.of("org.junit.Assert"));
    }

    protected void registerGenerator(Node node, Generator generator) {
        nodeGeneratorProvider.registerGenerator(node, generator);
    }

    private String getJavaCode(String code) {
        return javaCodeCache.computeIfAbsent(code, c -> generateJavaCode());
    }

    private Map<String, Object> executeProcessInstance(Map<String, Object> context) {
        try {
            ProcessInstance instance = getProcessInstance();
            return instance.execute(context);
        } catch (CompileFlowException e) {
            throw e;
        } catch (Exception e) {
            throw new CompileFlowException("Failed to execute process, code is " + code, e);
        }
    }

    private Map<String, Object> triggerProcessInstance(String tag, Map<String, Object> context) {
        try {
            StatefulProcessInstance instance = getProcessInstance();
            return instance.trigger(tag, context);
        } catch (Exception e) {
            throw new CompileFlowException("Failed to trigger process, code is " + code, e);
        }
    }

    private Map<String, Object> triggerProcessInstance(String tag, String event, Map<String, Object> context) {
        try {
            StatefulProcessInstance instance = getProcessInstance();
            return instance.trigger(tag, event, context);
        } catch (Exception e) {
            throw new CompileFlowException("Failed to trigger process, code is " + code, e);
        }
    }

    @SuppressWarnings("unchecked")
    protected <T extends ProcessInstance> T getProcessInstance() {
        Class<?> clazz = compiledClassCache.get(code);
        if (clazz == null) {
            throw new CompileFlowException("Failed to get compile class, code is " + code);
        }
        try {
            return ClassUtils.newInstance(clazz);
        } catch (Exception e) {
            throw new CompileFlowException("Failed to get process instance, code is " + code, e);
        }
    }

    private Class<?> compileJavaCode(String source) {
        return compileJavaCode(source, null);
    }

    private Class<?> compileJavaCode(String source, ClassLoader classLoader) {
        return COMPILER.compileJavaCode(classTarget.getFullName(), source, classLoader);
    }

    protected MethodTarget generateFlowMethod(String methodName,
                                              List<ParamTarget> paramTypes,
                                              Generator methodExecuteBodyGenerator, boolean stateful) {
        MethodTarget methodTarget = generateMethodDefinition(methodName, paramTypes);
        classTarget.addMethod(methodTarget);

        addVars(paramVars);
        addVars(returnVars);
        addVars(innerVars);

        if (CollectionUtils.isNotEmpty(paramVars)) {
            for (IVar paramVar : paramVars) {
                String dataVar = "_pContext.get(\"" + paramVar.getName() + "\")";
                Class<?> dateType = DataType.getJavaClass(paramVar.getDataType());
                if (dateType.isPrimitive()) {
                    String innerParamDefine = paramVar.getName() + " = (("
                        + DataType.getClassName(DataType.getPrimitiveClass(paramVar.getDataType()))
                        + ")DataType.transfer(" + dataVar + ", " + DataType.getClassName(paramVar.getDataType())
                        + ".class))." + DataType.getTransFunc(dateType) + ";";
                    methodTarget.addBodyLine(innerParamDefine);
                } else {
                    ClassWrapper pvType = ClassWrapper.of(paramVar.getDataType());
                    String innerParamDefine = paramVar.getName() + " = (" + pvType.getShortRawName()
                        + ")DataType.transfer("
                        + dataVar + ", " + pvType.getShortRawName() + ".class);";
                    methodTarget.addBodyLine(innerParamDefine);
                }
            }
        }

        methodTarget.addBodyLine("Map<String, Object> _pResult = new HashMap<>();");
        // add trigger status
        if (stateful && MethodConstants.EXECUTE_METHOD_NAME.equals(methodName)) {
            methodTarget.addBodyLine("boolean wait_event = true;");
        }
        methodTarget.addNewLine();
        methodExecuteBodyGenerator.generateCode(methodTarget);
        methodTarget.addNewLine();
        List<String> returnVarLines = wrapReturnVarLines();
        methodTarget.addBodyLines(returnVarLines);
        methodTarget.addBodyLine("return _pResult;");
        //if (monitorAction != null) {
        //    methodTarget.addBodyLines(monitorAction.generateCode());
        //}

        return methodTarget;
    }

    public List<String> wrapReturnVarLines() {
        List<String> returnVarLines = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(returnVars)) {
            for (IVar returnVar : returnVars) {
                returnVarLines.add("_pResult.put(\"" + returnVar.getName() + "\", " + returnVar.getName() + ");");
            }
        }
        return returnVarLines;
    }

    protected MethodTarget generateMethodDefinition(String methodName, List<ParamTarget> paramTypes) {
        MethodTarget methodTarget = new MethodTarget();
        methodTarget.setClassTarget(classTarget);
        methodTarget.setName(methodName);
        methodTarget.addException(ClassWrapper.of(Exception.class));
        if (CollectionUtils.isNotEmpty(paramTypes)) {
            paramTypes.forEach(methodTarget::addParameter);
        }
        ClassWrapper returnType = ClassWrapper.of("Map<String, Object>");
        methodTarget.setReturnType(returnType);
        methodTarget.addModifier(Modifier.PUBLIC);
        return methodTarget;
    }

    private void addVars(List<IVar> vars) {
        if (CollectionUtils.isNotEmpty(vars)) {
            for (IVar var : vars) {
                ClassWrapper rvType = ClassWrapper.of(var.getDataType());
                classTarget.addImportedType(rvType);
                String nullValue = DataType.getDefaultValueString(DataType.getJavaClass(var.getDataType()),
                    var.getDefaultValue());
                classTarget.addField(rvType, var.getName(), nullValue);
            }
        }
    }

    protected void generateExecuteMethodBody(CodeTargetSupport codeTargetSupport) {
        nodeGeneratorProvider.getGenerator(flowModel).generateCode(codeTargetSupport);
    }

    @SuppressWarnings("unchecked")
    private void initGatewayGraph() {
        buildGatewayGraph(flowModel);
    }

    private void buildGatewayGraph(NodeContainer<Node> nodeContainer) {
        List<TransitionNode> nodes = nodeContainer.getAllNodes()
            .stream()
            .filter(node -> node instanceof TransitionNode)
            .map(e -> (TransitionNode) e)
            .collect(Collectors.toList());

        nodes.forEach(this::buildFollowingNodes);
        nodes.stream()
            .filter(flowNode -> flowNode instanceof GatewayElement)
            .forEach(gatewayNode -> {
                gatewayNode.getOutgoingNodes().forEach(outgoingNode -> {
                    List<TransitionNode> branchNodes = buildBranchNodes(outgoingNode).stream()
                        .filter(node -> !followingGraph.get(gatewayNode.getId()).contains(node))
                        .collect(Collectors.toList());
                    branchGraph.put(outgoingNode.getId(), branchNodes);
                });

                if (CollectionUtils.isNotEmpty(gatewayNode.getIncomingNodes())
                    && gatewayNode.getIncomingNodes().stream()
                    .allMatch(incomingNode -> isContainedByIncomingNode(gatewayNode, incomingNode))) {
                    followingGraph.put(gatewayNode.getId(), Collections.emptyList());
                }
            });

        nodes.stream()
            .filter(flowNode -> flowNode instanceof NodeContainer)
            .map(e -> (NodeContainer) e)
            .forEach(this::buildGatewayGraph);
    }

    private List<TransitionNode> buildFollowingNodes(TransitionNode flowNode) {
        if (followingGraph.containsKey(flowNode.getId())) {
            return followingGraph.get(flowNode.getId());
        }

        List<TransitionNode> followingNodes;
        if (flowNode instanceof EndElement) {
            followingNodes = Collections.emptyList();
        } else if (flowNode instanceof WaitElement) {
            followingNodes = Collections.emptyList();
        } else if (flowNode instanceof GatewayElement) {
            followingNodes = buildGatewayFollowingNodes(flowNode);
        } else {
            followingNodes = new ArrayList<>();
            TransitionNode theOnlyOutgoingNode = getTheOnlyOutgoingNode(flowNode);
            if (theOnlyOutgoingNode != null) {
                followingNodes.add(theOnlyOutgoingNode);
                if (!(theOnlyOutgoingNode instanceof GatewayElement)) {
                    followingNodes.addAll(buildFollowingNodes(theOnlyOutgoingNode));
                }
            }
        }

        followingGraph.put(flowNode.getId(), followingNodes);
        return followingNodes;
    }

    private TransitionNode getTheOnlyOutgoingNode(TransitionNode flowNode) {
        if (flowNode.getOutgoingNodes().size() > 0) {
            return flowNode.getOutgoingNodes().get(0);
        }
        return null;
    }

    private boolean isContainedByIncomingNode(Node decisionNode, TransitionNode incomingNode) {
        if (incomingNode instanceof StartElement) {
            return false;
        }
        if (incomingNode instanceof GatewayElement) {
            List<TransitionNode> decisionFollowingNodes = followingGraph.get(decisionNode.getId());
            List<TransitionNode> incomingFollowingNodes = followingGraph.get(incomingNode.getId());
            if (decisionFollowingNodes.size() == incomingFollowingNodes.size()
                && incomingFollowingNodes.containsAll(decisionFollowingNodes)) {
                return true;
            }
        }
        return CollectionUtils.isNotEmpty(incomingNode.getIncomingNodes())
            && incomingNode.getIncomingNodes().stream()
            .allMatch(node -> isContainedByIncomingNode(decisionNode, node));
    }

    private List<TransitionNode> buildGatewayFollowingNodes(TransitionNode flowNode) {
        List<TransitionNode> outgoingNodes = flowNode.getOutgoingNodes();
        if (outgoingNodes.size() < 2) {
            return Collections.emptyList();
        }
        List<TransitionNode> followingNodes = Collections.emptyList();
        for (int i = 0; i < outgoingNodes.size(); i++) {
            TransitionNode branchNode = outgoingNodes.get(i);
            List<TransitionNode> branchFollowingNodes = buildFollowingNodes(branchNode);

            if (i == 0) {
                followingNodes = new ArrayList<>(branchFollowingNodes);
            } else {
                Iterator<TransitionNode> flowNodeIterator = followingNodes.iterator();
                while (flowNodeIterator.hasNext()) {
                    TransitionNode followingNode = flowNodeIterator.next();
                    if (branchFollowingNodes.stream()
                        .anyMatch(node -> node.getId().equals(followingNode.getId()))) {
                        break;
                    } else {
                        flowNodeIterator.remove();
                    }
                    if (CollectionUtils.isEmpty(followingNodes)) {
                        return followingNodes;
                    }
                }
            }
        }
        return followingNodes;
    }

    private List<TransitionNode> buildBranchNodes(TransitionNode branchNode) {
        if (branchGraph.containsKey(branchNode.getId())) {
            return branchGraph.get(branchNode.getId());
        }

        List<TransitionNode> branchNodes = new ArrayList<>();
        branchNodes.add(branchNode);
        if (!(branchNode instanceof EndElement) && !(branchNode instanceof WaitElement) && !(branchNode instanceof GatewayElement)) {
            TransitionNode theOnlyOutgoingNode = getTheOnlyOutgoingNode(branchNode);
            if (theOnlyOutgoingNode != null) {
                branchNodes.addAll(buildBranchNodes(theOnlyOutgoingNode));
            }
        }

        branchGraph.put(branchNode.getId(), branchNodes);
        return branchNodes;
    }

    private void validateRuntime() {
        List<ValidateMessage> validateMessages = validateFlowModel();

        if (CollectionUtils.isNotEmpty(validateMessages)) {
            List<ValidateMessage> validateFailResults = validateMessages.stream().filter(ValidateMessage::isFail)
                .collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(validateFailResults)) {
                throw new CompileFlowException(validateFailResults.toString());
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected List<ValidateMessage> validateFlowModel() {

        FlowModelValidator validator = ModelValidatorFactory.getFlowModelValidator(getFlowModelType());
        List<ValidateMessage> validateMessages = validator.validate(flowModel);
        List<TransitionNode> transitionNodes = flowModel.getTransitionNodes();
        if (isStateful()) {
            List<TransitionNode> noTagNodes = transitionNodes.stream().filter(node -> StringUtils.isEmpty(node.getTag()))
                .collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(noTagNodes)) {
                String noTagNodeIds = noTagNodes.stream().map(Node::getId).collect(Collectors.joining(","));
                validateMessages.add(ValidateMessage.fail("Nodes [" + noTagNodeIds + "] have no Tag"));
            }
        }
        return validateMessages;
    }

    private void initBeanProvider() {
        ApplicationContext contex = SpringApplicationContextProvider.applicationContext;
        SpringBeanHolder beanHolder = SpringBeanHolder.of(contex);
        BeanProvider.registerBeanHolder(beanHolder);
    }

    private void initScriptExecutorProvider() {
        registerScriptExecutor(new QLExecutor());
        registerScriptExecutor(new MvelExecutor());
    }

    private void registerScriptExecutor(ScriptExecutor scriptExecutor) {
        ScriptExecutorProvider.getInstance().registerScriptExecutor(scriptExecutor);
    }

    @SuppressWarnings("unchecked")
    protected void initGeneratorProvider() {
        GeneratorProviderFactory generatorProviderFactory = getGeneratorProviderFactory();
        nodeGeneratorProvider = generatorProviderFactory.create();
        registerContainerGenerator(flowModel);
        registerNodeGenerator(flowModel);
    }

    protected void registerContainerGenerator(NodeContainer nodeContainer) {
        registerGenerator(nodeContainer, GeneratorFactory.getInstance().getContainerGenerator(nodeContainer, this));
    }

    protected abstract GeneratorProviderFactory getGeneratorProviderFactory();


    private void initClassTarget() {
        String fullClassName = getFlowClassFullName(code, id);
        ClassWrapper classWrapper = ClassWrapper.of(fullClassName);
        classTarget.addModifier(Modifier.PUBLIC);
        classTarget.setPackageName(classWrapper.getPackageName());
        classTarget.setFullName(classWrapper.getName());
        classTarget.setName(classWrapper.getShortName());
        addJavaTypes();
    }

    private String getFlowClassFullName(String code, String id) {
        if (code == null) {
            throw new IllegalArgumentException("code is null");
        }
        int startIndex = code.lastIndexOf(".");
        if (startIndex > 0) {
            return wrapClassFullName(code.substring(0, startIndex) + "."
                + getFlowClassName(code.substring(startIndex + 1), id));
        }
        return wrapClassFullName(getFlowClassName(code, id));
    }

    private String getFlowTestClassFullName(String code, String id) {
        return getFlowClassFullName(code, id) + "_TEST";
    }

    private String wrapClassFullName(String name) {

        return "compileflow." + name;
    }

    private String getFlowClassName(String code, String id) {
        if (VarUtils.isLegalVarName(code)) {
            return StringUtils.capitalize(code) + "Flow";
        }
        if (VarUtils.isLegalVarName(id)) {
            return "Flow" + id;
        }
        return "Flow" + (code + id).hashCode();
    }

    private void addJavaTypes() {
        addImportedTypes();
        addExtImportedTypes();
    }

    private void addExtImportedTypes() {
        if (isStateful()) {
            addImportedType(StatefulProcessInstance.class);
        } else {
            addImportedType(ProcessInstance.class);
        }
    }

    private boolean isStateful() {
        return flowModel.getAllNodes().stream().anyMatch(e -> e instanceof WaitTaskNode || e instanceof WaitEventTaskNode);
    }

    private void addImportedTypes() {
        addImportedType(Map.class);
        addImportedType(HashMap.class);
        addImportedType(ObjectFactory.class);
        addImportedType(ProcessEngineFactory.class);
        addImportedType(DataType.class);
        addImportedType(BeanProvider.class);
    }

    public void addImportedType(Class<?> clazz) {
        classTarget.addImportedType(ClassWrapper.of(clazz));
    }

}
