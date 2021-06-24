package com.alibaba.compileflow.engine.runtime.impl;

import com.alibaba.compileflow.engine.common.ClassWrapper;
import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.constants.ProcessType;
import com.alibaba.compileflow.engine.definition.common.FlowModel;
import com.alibaba.compileflow.engine.definition.common.Node;
import com.alibaba.compileflow.engine.definition.common.TransitionNode;
import com.alibaba.compileflow.engine.definition.common.TransitionSupport;
import com.alibaba.compileflow.engine.process.preruntime.generator.Generator;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.MethodTarget;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.ParamTarget;
import com.alibaba.compileflow.engine.process.preruntime.validator.ValidateMessage;
import com.alibaba.compileflow.engine.runtime.instance.StatefulProcessInstance;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author wuxiang
 * @author yusu
 */
public abstract class AbstractStatefulProcessRuntime<T extends FlowModel> extends AbstractProcessRuntime<T> {

    public AbstractStatefulProcessRuntime(T flowModel) {
        super(flowModel);
    }

    @Override
    public Map<String, Object> start(Map<String, Object> context) {
        compile();
        return executeProcessInstance(context);
    }

    public Map<String, Object> trigger(String currentTag, Map<String, Object> context) {
        compile();
        return triggerProcessInstance(currentTag, context);
    }

    @Override
    public ProcessType getProcessType() {
        return ProcessType.STATEFUL;
    }

    @Override
    protected boolean isStateless() {
        return false;
    }

    @Override
    public String generateJavaCode() {
        classTarget.addSuperInterface(ClassWrapper.of(StatefulProcessInstance.class));
//        generateFlowMethod("execute", this::generateStartExecuteBody);
        generateStartExecuteMethod();
        MethodTarget method = generateFlowMethod("trigger", this::generateFireExecuteBody);
        ClassWrapper mType = ClassWrapper.of("String");
        method.addParameter(ParamTarget.of(mType, "currentTag"));
        classTarget.addNewLine();
        return classTarget.generateCode();
    }

    @Override
    protected List<Class<?>> getExtImportedTypes() {
        return Collections.singletonList(StatefulProcessInstance.class);
    }

    @Override
    protected List<ValidateMessage> validateFlowModel() {
        List<ValidateMessage> validateMessages = super.validateFlowModel();
        List<TransitionNode> transitionNodes = flowModel.getTransitionNodes();
        List<TransitionNode> noTagNodes = transitionNodes.stream().filter(node -> StringUtils.isEmpty(node.getTag()))
            .collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(noTagNodes)) {
            String noTagNodeIds = noTagNodes.stream().map(Node::getId).collect(Collectors.joining(","));
            validateMessages.add(ValidateMessage.fail("Nodes [" + noTagNodeIds + "] have no Tag"));
        }
        return validateMessages;
    }

    private Map<String, Object> executeProcessInstance(Map<String, Object> context) {
        try {
            StatefulProcessInstance instance = getProcessInstance();
            return instance.execute(context);
        } catch (CompileFlowException e) {
            throw e;
        } catch (Exception e) {
            throw new CompileFlowException("Failed to execute process, code is " + code, e);
        }
    }

    private Map<String, Object> triggerProcessInstance(String currentTag, Map<String, Object> context) {
        try {
            StatefulProcessInstance instance = getProcessInstance();
            return instance.trigger(context, currentTag);
        } catch (Exception e) {
            throw new CompileFlowException("Failed to trigger process, code is " + code, e);
        }
    }

//    private void generateStartExecuteBody(CodeTargetSupport method) {
//        method.addBodyLine("");
//        method.addBodyLine("_pResult = trigger(_pContext, \"" + flowModel.getStartNode().getTag() + "\");");
//        method.addBodyLine("");
//    }

    private void generateStartExecuteMethod() {
        MethodTarget methodTarget = generateMethodDefinition("execute");
        methodTarget.addBodyLine("return ProcessEngineFactory.getProcessEngine().execute(\"" + code + "\", _pContext);");
        // 这里增加一下process代码
//        methodTarget.addBodyLine("return null;");
        classTarget.addMethod(methodTarget);
    }

    private void generateFireExecuteBody(CodeTargetSupport method) {

        method.addBodyLine("boolean running = true;");
        method.addBodyLine("boolean trigger = true;");
        method.addBodyLine("String _event = (String)_pContext.get(\"eventName\") == null?\"\" :(String)_pContext.get(\"eventName\");");
        method.addNewLine();
        method.addBodyLine("while (running) {");
        method.addBodyLine("switch (currentTag) {");
        Set<String> visitedNodes = new HashSet<>();
        List<TransitionNode> runtimeNodes = flowModel.getRuntimeNodes();
        for (TransitionNode runtimeNode : runtimeNodes) {
            if (visitedNodes.contains(runtimeNode.getId())) {
                continue;
            }

            Generator generator = getNodeGeneratorProvider().getGenerator(runtimeNode);
            String nodeTag = runtimeNode.getTag();
            method.addBodyLine("case \"" + nodeTag + "\": {");
            generator.generateCode(method);
            List<TransitionSupport> transitions = runtimeNode.getTransitions();
            if (CollectionUtils.isNotEmpty(transitions)) {
                for (TransitionSupport transition : transitions) {
                    String condition = transition.getExpression();
                    boolean hasCondition = StringUtils.isNotEmpty(condition);
                    if (hasCondition) {
                        method.addBodyLine("if (" + condition + ") {");
                    }
                    Node toNode = flowModel.getNode(transition.getTarget());
                    method.addBodyLine("currentTag = \"" + toNode.getTag() + "\";");
                    method.addBodyLine("break;");
                    if (hasCondition) {
                        method.addBodyLine("}");
                    }
                }
            } else {
                method.addBodyLine("break;");
            }
            method.addBodyLine("}");
            visitedNodes.add(runtimeNode.getId());
        }
        method.addBodyLine("default: {");
        method.addBodyLine("running = false;");
        method.addBodyLine("}");
        method.addBodyLine("}");
        method.addBodyLine("");
        method.addBodyLine("if (trigger) {");
        method.addBodyLine("trigger = false;");
        method.addBodyLine("}");
        method.addBodyLine("}");
        method.addBodyLine("");
    }

}
