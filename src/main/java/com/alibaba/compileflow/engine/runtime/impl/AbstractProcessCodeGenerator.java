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

import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.common.ClassWrapper;
import com.alibaba.compileflow.engine.common.constants.FlowModelType;
import com.alibaba.compileflow.engine.common.util.DataType;
import com.alibaba.compileflow.engine.common.util.ObjectFactory;
import com.alibaba.compileflow.engine.common.util.VarUtils;
import com.alibaba.compileflow.engine.definition.common.FlowModel;
import com.alibaba.compileflow.engine.definition.common.Node;
import com.alibaba.compileflow.engine.definition.common.NodeContainer;
import com.alibaba.compileflow.engine.definition.common.TransitionNode;
import com.alibaba.compileflow.engine.definition.common.var.IVar;
import com.alibaba.compileflow.engine.process.builder.generator.Generator;
import com.alibaba.compileflow.engine.process.builder.generator.bean.BeanProvider;
import com.alibaba.compileflow.engine.process.builder.generator.code.ClassTarget;
import com.alibaba.compileflow.engine.process.builder.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.process.builder.generator.code.MethodTarget;
import com.alibaba.compileflow.engine.process.builder.generator.code.ParamTarget;
import com.alibaba.compileflow.engine.process.builder.generator.constansts.MethodConstants;
import com.alibaba.compileflow.engine.process.builder.generator.constansts.Modifier;
import com.alibaba.compileflow.engine.process.builder.generator.factory.GeneratorFactory;
import com.alibaba.compileflow.engine.process.builder.generator.impl.EventTriggerMethodGenerator;
import com.alibaba.compileflow.engine.process.builder.generator.impl.TriggerMethodGenerator;
import com.alibaba.compileflow.engine.process.builder.generator.provider.NodeGeneratorProvider;
import com.alibaba.compileflow.engine.runtime.ProcessCodeGenerator;
import com.alibaba.compileflow.engine.runtime.instance.ProcessInstance;
import com.alibaba.compileflow.engine.runtime.instance.StatefulProcessInstance;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates the Java source code for a given process runtime.
 * This class encapsulates all logic related to Java code generation.
 *
 * @param <T> The type of the FlowModel.
 * @author yusu
 */
public abstract class AbstractProcessCodeGenerator<T extends FlowModel, R extends AbstractProcessRuntime<T>>
        implements ProcessCodeGenerator {

    private static final String CONTEXT_VAR_NAME = "_pContext";
    private static final String RESULT_VAR_NAME = "_pResult";
    private static final String WRAP_RESULT_METHOD_NAME = "_wrapResult";

    private static final Map<String, String> javaCodeCache = new ConcurrentHashMap<>();

    protected final AbstractProcessRuntime<T> runtime;
    private final T flowModel;
    private final ClassTarget classTarget;
    private final List<IVar> paramVars;
    private final List<IVar> returnVars;
    private final List<IVar> innerVars;
    private NodeGeneratorProvider nodeGeneratorProvider;

    public AbstractProcessCodeGenerator(R runtime) {
        this.runtime = runtime;
        this.flowModel = runtime.getFlowModel();
        this.paramVars = flowModel.getParamVars();
        this.returnVars = flowModel.getReturnVars();
        this.innerVars = flowModel.getInnerVars();
        this.classTarget = new ClassTarget();
        nodeGeneratorProvider = buildNodeGeneratorProvider();
        initClassTarget();
        initGeneratorProvider();
    }

    @Override
    public NodeGeneratorProvider getNodeGeneratorProvider() {
        return nodeGeneratorProvider;
    }

    @Override
    public String generateCode() {
        return generateCode(true);
    }

    @Override
    public String generateCode(boolean useCache) {
        if (useCache) {
            return javaCodeCache.computeIfAbsent(runtime.code, c -> doGenerateCode());
        }
        return doGenerateCode();
    }

    @Override
    public String regenerateCode() {
        invalidateCodeCache();
        return generateCode();
    }

    public void invalidateCodeCache() {
        javaCodeCache.remove(runtime.code);
    }

    public String doGenerateCode() {
        if (runtime.isStateful()) {
            classTarget.addSuperInterface(ClassWrapper.of(StatefulProcessInstance.class));
        } else {
            classTarget.addSuperInterface(ClassWrapper.of(ProcessInstance.class));
        }

        // Generate the main `execute` method
        generateFlowMethod(MethodConstants.EXECUTE_METHOD_NAME,
                Collections.singletonList(ParamTarget.of(ClassWrapper.of("Map<String, Object>"), CONTEXT_VAR_NAME)),
                this::generateExecuteMethodBody);

        // Generate `trigger` methods if the process is stateful
        if (runtime.isStateful()) {
            generateTriggerMethods();
        }

        // Generate the helper method for wrapping results
        generateWrapResultMethod();

        return classTarget.generateCode();
    }

    public String generateTestCode() {
        ClassTarget testClassTarget = new ClassTarget();
        String code = runtime.code;
        String id = runtime.id;
        String fullTestClassName = getFlowTestClassFullName(code, id);
        ClassWrapper classWrapper = ClassWrapper.of(fullTestClassName);
        testClassTarget.addModifier(Modifier.PUBLIC);
        testClassTarget.setPackageName(classWrapper.getPackageName());
        testClassTarget.setName(classWrapper.getShortName());
        testClassTarget.setSuperClass(ClassWrapper.of("junit.framework.TestCase"));

        // Add necessary imports
        testClassTarget.addImportedType(ClassWrapper.of("org.junit.Test"));
        testClassTarget.addImportedType(ClassWrapper.of("org.junit.Assert"));
        testClassTarget.addImportedType(ClassWrapper.of(ProcessEngine.class));
        testClassTarget.addImportedType(ClassWrapper.of(ProcessEngineFactory.class));
        testClassTarget.addImportedType(ClassWrapper.of(Map.class));
        testClassTarget.addImportedType(ClassWrapper.of(HashMap.class));
        classTarget.addImportedType(ClassWrapper.of(FlowModelType.class));

        addImportedType(testClassTarget);

        MethodTarget method = new MethodTarget();
        method.addAnnotation("@Test");
        method.addModifier(Modifier.PUBLIC);
        method.setName("testProcess");
        method.addException(ClassWrapper.of(Exception.class));

        method.addBodyLine("String code = \"" + code + "\";");
        String engineCode = generateGetEngineCode();
        method.addBodyLine(engineCode);
        method.addBodyLine("System.out.println(engine.getJavaCode(code));");
        method.addBodyLine("Map<String, Object> context = new HashMap<>();");

        if (CollectionUtils.isNotEmpty(this.paramVars)) {
            for (IVar v : this.paramVars) {
                String defaultValue = DataType.getDefaultValueString(DataType.getJavaClass(v.getDataType()), v.getDefaultValue());
                method.addBodyLine("context.put(\"" + v.getName() + "\", " + defaultValue + ");");
            }
        }

        method.addBodyLine("try {");
        method.addBodyLine("    System.out.println(engine.execute(code, context));");
        method.addBodyLine("} catch (Exception e) {");
        method.addBodyLine("    e.printStackTrace();");
        method.addBodyLine("    Assert.fail(e.getMessage());");
        method.addBodyLine("}");

        testClassTarget.addMethod(method);
        return testClassTarget.generateCode();
    }

    @Override
    public String getClassFullName() {
        return classTarget.getFullName();
    }

    protected abstract String generateGetEngineCode();

    protected abstract void addImportedType(ClassTarget testClassTarget);

    private void addDefaultImports() {
        addImportedType(Map.class);
        addImportedType(HashMap.class);
        addImportedType(ObjectFactory.class);
        addImportedType(ProcessEngineFactory.class);
        addImportedType(DataType.class);
        addImportedType(BeanProvider.class);
        if (runtime.isStateful()) {
            addImportedType(StatefulProcessInstance.class);
        } else {
            addImportedType(ProcessInstance.class);
        }
    }

    protected void addImportedType(Class<?> clazz) {
        classTarget.addImportedType(ClassWrapper.of(clazz));
    }

    private void generateFlowMethod(String methodName, List<ParamTarget> params, Generator methodBodyGenerator) {
        MethodTarget methodTarget = createBaseMethodTarget(methodName, params);
        generateContextToVarsMapping(methodTarget, paramVars);
        methodTarget.addNewLine();
        methodBodyGenerator.generateCode(methodTarget);
        methodTarget.addNewLine();
        methodTarget.addBodyLine("return " + WRAP_RESULT_METHOD_NAME + "();");
        classTarget.addMethod(methodTarget);
    }

    private void generateTriggerMethods() {
        // Generate: trigger(String tag, Map<String, Object> _pContext)
        List<ParamTarget> triggerParams = Arrays.asList(
                ParamTarget.of(ClassWrapper.of("String"), "tag"),
                ParamTarget.of(ClassWrapper.of("Map<String, Object>"), CONTEXT_VAR_NAME)
        );
        MethodTarget triggerMethod = createBaseMethodTarget(MethodConstants.TRIGGER_METHOD_NAME, triggerParams);
        new TriggerMethodGenerator().generateCode(triggerMethod);
        classTarget.addMethod(triggerMethod);

        // Generate: trigger(String tag, String event, Map<String, Object> _pContext)
        List<ParamTarget> eventTriggerParams = Arrays.asList(
                ParamTarget.of(ClassWrapper.of("String"), "tag"),
                ParamTarget.of(ClassWrapper.of("String"), "event"),
                ParamTarget.of(ClassWrapper.of("Map<String, Object>"), CONTEXT_VAR_NAME)
        );
        generateFlowMethod(MethodConstants.TRIGGER_METHOD_NAME, eventTriggerParams,
                new EventTriggerMethodGenerator(runtime));
    }

    private void generateWrapResultMethod() {
        MethodTarget methodTarget = createBaseMethodTarget(WRAP_RESULT_METHOD_NAME, Collections.emptyList());
        methodTarget.addBodyLine("Map<String, Object> " + RESULT_VAR_NAME + " = new HashMap<>();");

        if (CollectionUtils.isNotEmpty(returnVars)) {
            for (IVar returnVar : returnVars) {
                methodTarget.addBodyLine(RESULT_VAR_NAME + ".put(\"" + returnVar.getName() + "\", " + returnVar.getName() + ");");
            }
        }
        methodTarget.addBodyLine("return " + RESULT_VAR_NAME + ";");
        classTarget.addMethod(methodTarget);
    }

    private MethodTarget createBaseMethodTarget(String methodName, List<ParamTarget> params) {
        MethodTarget methodTarget = new MethodTarget();
        methodTarget.setClassTarget(classTarget);
        methodTarget.setName(methodName);
        methodTarget.addModifier(Modifier.PUBLIC);
        methodTarget.setReturnType(ClassWrapper.of("Map<String, Object>"));
        methodTarget.addException(ClassWrapper.of(Exception.class));
        if (CollectionUtils.isNotEmpty(params)) {
            params.forEach(methodTarget::addParameter);
        }
        return methodTarget;
    }

    private void addClassFieldsForVars(List<IVar> vars) {
        if (CollectionUtils.isNotEmpty(vars)) {
            for (IVar var : vars) {
                ClassWrapper varType = ClassWrapper.of(var.getDataType());
                classTarget.addImportedType(varType);
                String defaultValue = DataType.getDefaultValueString(DataType.getJavaClass(var.getDataType()), var.getDefaultValue());
                classTarget.addField(varType, var.getName(), defaultValue);
            }
        }
    }

    private void generateContextToVarsMapping(MethodTarget methodTarget, List<IVar> vars) {
        if (CollectionUtils.isEmpty(vars)) {
            return;
        }

        for (IVar paramVar : vars) {
            String contextGet = CONTEXT_VAR_NAME + ".get(\"" + paramVar.getName() + "\")";
            Class<?> javaType = DataType.getJavaClass(paramVar.getDataType());
            String line;
            if (javaType.isPrimitive()) {
                String wrapperName = DataType.getClassName(DataType.getPrimitiveClass(paramVar.getDataType()));
                String transferCall = "(" + wrapperName + ")DataType.transfer(" + contextGet + ", " + wrapperName + ".class)";
                line = paramVar.getName() + " = ((" + transferCall + "))." + DataType.getTransFunc(javaType) + ";";
            } else {
                ClassWrapper pvType = ClassWrapper.of(paramVar.getDataType());
                String typeName = pvType.getShortRawName();
                line = paramVar.getName() + " = (" + typeName + ")DataType.transfer(" + contextGet + ", " + typeName + ".class);";
            }
            methodTarget.addBodyLine(line);
        }
    }

    protected void generateExecuteMethodBody(CodeTargetSupport codeTargetSupport) {
        nodeGeneratorProvider.getGenerator(flowModel).generateCode(codeTargetSupport);
    }

    private void initClassTarget() {
        String fullClassName = getFlowClassFullName(runtime.code, runtime.id);
        ClassWrapper classWrapper = ClassWrapper.of(fullClassName);
        classTarget.addModifier(Modifier.PUBLIC);
        classTarget.setPackageName(classWrapper.getPackageName());
        classTarget.setFullName(classWrapper.getName());
        classTarget.setName(classWrapper.getShortName());

        addDefaultImports();
        addClassFieldsForVars(paramVars);
        addClassFieldsForVars(returnVars);
        addClassFieldsForVars(innerVars);
    }

    private String getFlowClassFullName(String code, String id) {
        if (code == null) {
            throw new IllegalArgumentException("Process code cannot be null.");
        }
        int lastDotIndex = code.lastIndexOf('.');
        String basePackage = (lastDotIndex > 0) ? code.substring(0, lastDotIndex) : "";
        String simpleName = (lastDotIndex > 0) ? code.substring(lastDotIndex + 1) : code;
        String className = getFlowClassName(simpleName, id);

        String qualifiedName = StringUtils.isEmpty(basePackage) ? className : basePackage + "." + className;
        return "compileflow." + qualifiedName;
    }

    private String getFlowTestClassFullName(String code, String id) {
        return getFlowClassFullName(code, id) + "Test";
    }

    private String getFlowClassName(String code, String id) {
        if (VarUtils.isLegalVarName(code)) {
            return StringUtils.capitalize(code) + "Flow";
        }
        if (VarUtils.isLegalVarName(id)) {
            return "Flow" + id;
        }
        return "Flow" + Math.abs((code + id).hashCode());
    }

    @SuppressWarnings("unchecked")
    protected void initGeneratorProvider() {
        registerGenerator(flowModel, GeneratorFactory.getInstance().getContainerGenerator(flowModel, runtime));
        registerNodeGenerator(flowModel);
    }

    protected abstract NodeGeneratorProvider buildNodeGeneratorProvider();


    protected abstract void registerNodeGenerator(NodeContainer<TransitionNode> nodeContainer);

    protected void registerGenerator(Node node, Generator generator) {
        nodeGeneratorProvider.registerGenerator(node, generator);
    }

}
