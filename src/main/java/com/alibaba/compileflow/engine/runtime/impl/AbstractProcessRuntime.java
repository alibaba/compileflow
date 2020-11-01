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
import com.alibaba.compileflow.engine.common.constants.FlowModelType;
import com.alibaba.compileflow.engine.common.utils.ClassUtils;
import com.alibaba.compileflow.engine.common.utils.DataType;
import com.alibaba.compileflow.engine.common.utils.ObjectFactory;
import com.alibaba.compileflow.engine.common.utils.VarUtils;
import com.alibaba.compileflow.engine.definition.common.FlowModel;
import com.alibaba.compileflow.engine.definition.common.Node;
import com.alibaba.compileflow.engine.definition.common.NodeContainer;
import com.alibaba.compileflow.engine.definition.common.TransitionNode;
import com.alibaba.compileflow.engine.definition.common.var.IVar;
import com.alibaba.compileflow.engine.process.preruntime.compiler.Compiler;
import com.alibaba.compileflow.engine.process.preruntime.compiler.impl.CompilerImpl;
import com.alibaba.compileflow.engine.process.preruntime.generator.Generator;
import com.alibaba.compileflow.engine.process.preruntime.generator.bean.BeanProvider;
import com.alibaba.compileflow.engine.process.preruntime.generator.bean.SpringApplicationContextProvider;
import com.alibaba.compileflow.engine.process.preruntime.generator.bean.SpringBeanHolder;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.ClassTarget;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.MethodTarget;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.ParamTarget;
import com.alibaba.compileflow.engine.process.preruntime.generator.constansts.Modifier;
import com.alibaba.compileflow.engine.process.preruntime.generator.factory.GeneratorFactory;
import com.alibaba.compileflow.engine.process.preruntime.generator.factory.GeneratorProviderFactory;
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
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * @author wuxiang
 * @author yusu
 */
public abstract class AbstractProcessRuntime<T extends FlowModel> implements ProcessRuntime {

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

    public void recompile(String code) {
        compiledClassCache.computeIfPresent(code, (k, v) -> compileJavaCode(generateJavaCode()));
    }

    @Override
    public Map<String, Object> start(Map<String, Object> context) {
        compile();
        return executeProcessInstance(context);
    }

    public abstract FlowModelType getFlowModelType();

    public abstract String generateJavaCode();

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
//        String engineCode = isStateless() && isBpmn20() ? "StatelessProcessEngine engine = "
//            + "ProcessEngineFactory.getStatelessProcessEngine(FlowModelType.BPMN);"
//            : !isStateless() && !isBpmn20() ? "StatelessProcessEngine engine = "
//                + "ProcessEngineFactory.getStatefulProcessEngine();"
//                : !isStateless() && isBpmn20() ? "StatelessProcessEngine engine = "
//                    + "ProcessEngineFactory.getStatefulProcessEngine(FlowModelType.BPMN);"
//                    : "StatelessProcessEngine engine = ProcessEngineFactory.getProcessEngine();";
        String engineCode = isBpmn20() ? "ProcessEngine<BpmnModel> engine = ProcessEngineFactory.getProcessEngine(FlowModelType.BPMN);"
            : "ProcessEngine<TbbpmModel> engine = ProcessEngineFactory.getProcessEngine();";
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
        method.addBodyLine("System.out.println(engine.start(code, context));");
        method.addBodyLine("}");
        method.addBodyLine("catch (Exception e) {");
        method.addBodyLine("e.printStackTrace();");
        method.addBodyLine("Assert.fail(e.getMessage());");
        method.addBodyLine("}");
        classTarget.addMethod(method);
        return classTarget.generateCode();
    }

    protected abstract boolean isStateless();

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

    public Node getNodeById(String id) {
        return flowModel.getNode(id);
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

    @SuppressWarnings("unchecked")
    private <T extends ProcessInstance> T getProcessInstance() {
        Class<?> clazz = compiledClassCache.get(code);
        if (clazz == null) {
            throw new CompileFlowException("Failed to get compile class, code is " + code);
        }
        try {
            return (T) ClassUtils.newInstance(clazz);
        } catch (Exception e) {
            throw new CompileFlowException("Failed to get process instance, code is " + code, e);
        }
    }

    private Class<?> compileJavaCode(String source) {
        return COMPILER.compileJavaCode(classTarget.getFullName(), source);
    }

    protected MethodTarget generateFlowMethod(String methodName,
                                              Generator methodExecuteBodyGenerator) {
        MethodTarget methodTarget = new MethodTarget();
        classTarget.addMethod(methodTarget);
        methodTarget.setClassTarget(classTarget);
        methodTarget.setName(methodName);
        methodTarget.addException(ClassWrapper.of(Exception.class));
        ClassWrapper mType = ClassWrapper.of("Map<String, Object>");
        methodTarget.addParameter(ParamTarget.of(mType, "_pContext"));

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
        methodExecuteBodyGenerator.generateCode(methodTarget);
        List<String> results = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(returnVars)) {
            for (IVar returnVar : returnVars) {
                results.add("_pResult.put(\"" + returnVar.getName() + "\", " + returnVar.getName() + ");");
            }
            methodTarget.addBodyLines(results);
        }
        methodTarget.addBodyLine("return _pResult;");
        methodTarget.setReturnType(mType);
        methodTarget.addModifier(Modifier.PUBLIC);
        //if (monitorAction != null) {
        //    methodTarget.addBodyLines(monitorAction.generateCode());
        //}

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

    public void init() {
        validateRuntime();
        initClassTarget();
        initGeneratorProvider();
        if (inited.compareAndSet(false, true)) {
            initBeanProvider();
            initScriptExecutorProvider();
        }
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
        //Class<? extends FlowModelValidator> validatorClass = FlowModelType.BPMN.equals(getFlowModelType())
        //    ? BpmnModelValidator.class : TbbpmModelValidator.class;
        //return ExtensionInvoker.getInstance().invoke(FlowModelValidator.CODE, validatorClass,
        //    ReduceFilter.allCollectionNonEmpty(), flowModel);
        FlowModelValidator validator = ModelValidatorFactory.getFlowModelValidator(getFlowModelType());
        return validator.validate(flowModel);
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
        if (generatorProviderFactory == null) {
            throw new CompileFlowException("GeneratorProviderFactory is null");
        }
        nodeGeneratorProvider = generatorProviderFactory.create();
        if (nodeGeneratorProvider == null) {
            throw new CompileFlowException("NodeGeneratorProvider is null");
        }
        registerContainerGenerator(flowModel);
        registerNodeGenerator(flowModel);
    }

    protected void registerContainerGenerator(NodeContainer nodeContainer) {
        registerGenerator(nodeContainer, GeneratorFactory.getInstance().getContainerGenerator(nodeContainer, this));
    }

    protected abstract GeneratorProviderFactory getGeneratorProviderFactory();

    protected abstract void registerNodeGenerator(NodeContainer<TransitionNode> nodeContainer);

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
        List<Class<?>> extImportedTypes = getExtImportedTypes();
        if (CollectionUtils.isNotEmpty(extImportedTypes)) {
            for (Class<?> extImportedType : extImportedTypes) {
                classTarget.addImportedType(ClassWrapper.of(extImportedType));
            }
        }
    }

    protected abstract List<Class<?>> getExtImportedTypes();

    private void addImportedTypes() {
        classTarget.addImportedType(ClassWrapper.of(Map.class));
        classTarget.addImportedType(ClassWrapper.of(HashMap.class));
        classTarget.addImportedType(ClassWrapper.of(ObjectFactory.class));
        classTarget.addImportedType(ClassWrapper.of(DataType.class));
        classTarget.addImportedType(ClassWrapper.of(BeanProvider.class));
    }

}