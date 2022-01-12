package com.alibaba.compileflow.engine.process.preruntime.generator.impl;

import com.alibaba.compileflow.engine.definition.common.FlowModel;
import com.alibaba.compileflow.engine.definition.common.Node;
import com.alibaba.compileflow.engine.definition.common.TransitionNode;
import com.alibaba.compileflow.engine.definition.common.TransitionSupport;
import com.alibaba.compileflow.engine.process.preruntime.generator.Generator;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author yusu
 */
public class EventTriggerMethodGenerator extends AbstractGenerator {

    private AbstractProcessRuntime runtime;

    public EventTriggerMethodGenerator(AbstractProcessRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
        codeTargetSupport.addBodyLine("boolean running = true;");
        codeTargetSupport.addBodyLine("boolean trigger = true;");
        codeTargetSupport.addNewLine();
        codeTargetSupport.addBodyLine("while (running) {");
        codeTargetSupport.addBodyLine("switch (tag) {");
        Set<String> visitedNodes = new HashSet<>();
        FlowModel flowModel = runtime.getFlowModel();
        List<TransitionNode> runtimeNodes = flowModel.getRuntimeNodes();
        for (TransitionNode runtimeNode : runtimeNodes) {
            if (visitedNodes.contains(runtimeNode.getId())) {
                continue;
            }

            Generator generator = runtime.getNodeGeneratorProvider().getGenerator(runtimeNode);
            String nodeTag = runtimeNode.getTag();
            codeTargetSupport.addBodyLine("case \"" + nodeTag + "\": {");
            generator.generateCode(codeTargetSupport);
            List<TransitionSupport> transitions = runtimeNode.getTransitions();
            if (CollectionUtils.isNotEmpty(transitions)) {
                for (TransitionSupport transition : transitions) {
                    String condition = transition.getExpression();
                    boolean hasCondition = StringUtils.isNotEmpty(condition);
                    if (hasCondition) {
                        codeTargetSupport.addBodyLine("if (" + condition + ") {");
                    }
                    Node toNode = flowModel.getNode(transition.getTarget());
                    codeTargetSupport.addBodyLine("tag = \"" + toNode.getTag() + "\";");
                    codeTargetSupport.addBodyLine("break;");
                    if (hasCondition) {
                        codeTargetSupport.addBodyLine("}");
                    }
                }
            } else {
                codeTargetSupport.addBodyLine("break;");
            }
            codeTargetSupport.addBodyLine("}");
            visitedNodes.add(runtimeNode.getId());
        }
        codeTargetSupport.addBodyLine("default: {");
        codeTargetSupport.addBodyLine("running = false;");
        codeTargetSupport.addBodyLine("}");
        codeTargetSupport.addBodyLine("}");
        codeTargetSupport.addBodyLine("");
        codeTargetSupport.addBodyLine("if (trigger) {");
        codeTargetSupport.addBodyLine("trigger = false;");
        codeTargetSupport.addBodyLine("}");
        codeTargetSupport.addBodyLine("}");
    }

}
