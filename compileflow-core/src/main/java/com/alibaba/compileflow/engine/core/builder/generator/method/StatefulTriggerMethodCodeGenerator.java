package com.alibaba.compileflow.engine.core.builder.generator.method;

import com.alibaba.compileflow.engine.core.builder.generator.AbstractCodeGenerator;
import com.alibaba.compileflow.engine.core.builder.generator.CodeGenerator;
import com.alibaba.compileflow.engine.core.builder.generator.GeneratorContext;
import com.alibaba.compileflow.engine.core.builder.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.core.definition.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates the trigger method for stateful processes:
 * loop + switch(tag) + node handlers + transition routing with safety guards.
 *
 * @author yusu
 */
public class StatefulTriggerMethodCodeGenerator extends AbstractCodeGenerator {

    private final GeneratorContext context;

    public StatefulTriggerMethodCodeGenerator(GeneratorContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Generator context cannot be null");
        }
        this.context = context;
    }

    /**
     * Generate the event-driven trigger method.
     */
    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
        generateLoopStructure(codeTargetSupport);
        generateAllNodeCases(codeTargetSupport);
        generateDefaultCase(codeTargetSupport);
        generateLoopClosure(codeTargetSupport);
    }

    /**
     * Generate loop structure and switch.
     */
    private void generateLoopStructure(CodeTargetSupport codeTarget) {
        codeTarget.addBodyLine("boolean running = true;");
        codeTarget.addBodyLine("boolean trigger = true;");
        codeTarget.addNewLine();
        codeTarget.addBodyLine("while (running) {");
        codeTarget.addBodyLine("switch (tag) {");
    }

    /**
     * Generate all node cases and transitions.
     */
    private void generateAllNodeCases(CodeTargetSupport codeTarget) {
        Set<String> visited = new HashSet<>();
        FlowModel<TransitionNode> flowModel = context.getFlowModel();
        List<TransitionNode> runtimeNodes = flowModel.getRuntimeNodes();

        for (TransitionNode node : runtimeNodes) {
            if (!visited.add(node.getId())) {
                continue;
            }

            String nodeTag = node.getTag();
            codeTarget.addBodyLine("case \"" + nodeTag + "\": {");

            // Node logic
            CodeGenerator generator = context.getGenerator(node);
            generator.generateCode(codeTarget);

            // Transitions
            generateNodeTransition(codeTarget, node);

            codeTarget.addBodyLine("}");
        }
    }

    /**
     * Generate transition logic for a node.
     */
    private void generateNodeTransition(CodeTargetSupport codeTarget, TransitionNode node) {
        if (node instanceof GatewayNode && !(node instanceof ExclusiveGatewayElement)) {
            // Gateway transition: jump to implicit merge successor
            List<TransitionNode> nexts = context.getFollowingGraph().get(node.getId());
            if (CollectionUtils.isEmpty(nexts)) {
                codeTarget.addBodyLine("running = false;");
                codeTarget.addBodyLine("break;");
            } else {
                codeTarget.addBodyLine("tag = \"" + nexts.get(0).getTag() + "\";");
                codeTarget.addBodyLine("break;");
            }
        } else {
            // Regular node: handle outgoing transitions
            List<Transition> transitions = node.getOutgoingTransitions();
            if (CollectionUtils.isNotEmpty(transitions)) {
                generateTransitionLogic(codeTarget, transitions, node);
            } else {
                codeTarget.addBodyLine("running = false;");
                codeTarget.addBodyLine("break;");
            }
        }
    }

    /**
     * Generate conditional/default transition logic.
     */
    private void generateTransitionLogic(CodeTargetSupport codeTarget, List<Transition> transitions, TransitionNode node) {
        // Split conditional/default
        Transition defaultTrans = null;
        List<Transition> condTrans = new java.util.ArrayList<>();

        for (Transition transition : transitions) {
            String expr = transition.getExpression();
            if (StringUtils.isNotEmpty(expr)) {
                condTrans.add(transition);
            } else if (defaultTrans == null) {
                defaultTrans = transition;
            }
        }

        if (condTrans.isEmpty()) {
            // Only default
            if (defaultTrans != null) {
                Node to = context.getFlowModel().getNode(defaultTrans.getTarget());
                codeTarget.addBodyLine("tag = \"" + to.getTag() + "\";");
                codeTarget.addBodyLine("break;");
            } else {
                codeTarget.addBodyLine("running = false;");
                codeTarget.addBodyLine("break;");
            }
        } else {
            // Conditional chain
            for (int i = 0; i < condTrans.size(); i++) {
                Transition t = condTrans.get(i);
                Node to = context.getFlowModel().getNode(t.getTarget());
                String expr = t.getExpression();

                if (i == 0) {
                    codeTarget.addBodyLine("if (" + expr + ") {");
                } else {
                    codeTarget.addBodyLine("} else if (" + expr + ") {");
                }
                codeTarget.addBodyLine("tag = \"" + to.getTag() + "\";");
                codeTarget.addBodyLine("break;");
            }

            // Default transition
            if (defaultTrans != null) {
                Node to = context.getFlowModel().getNode(defaultTrans.getTarget());
                codeTarget.addBodyLine("} else {");
                codeTarget.addBodyLine("tag = \"" + to.getTag() + "\";");
                codeTarget.addBodyLine("break;");
                codeTarget.addBodyLine("}");
            } else {
                codeTarget.addBodyLine("} else {");
                codeTarget.addBodyLine("running = false;");
                codeTarget.addBodyLine("break;");
                codeTarget.addBodyLine("}");
            }
        }
    }

    /**
     * Generate default case.
     */
    private void generateDefaultCase(CodeTargetSupport codeTarget) {
        codeTarget.addBodyLine("default: {");
        codeTarget.addBodyLine("running = false;");
        codeTarget.addBodyLine("break;");
        codeTarget.addBodyLine("}");
    }

    /**
     * Generate loop closure and trigger flip.
     */
    private void generateLoopClosure(CodeTargetSupport codeTarget) {
        codeTarget.addBodyLine("}");
        // After the first tick, flip trigger=false to enable inAction branches for stateful nodes
        codeTarget.addBodyLine("if (trigger) { trigger = false; }");
        codeTarget.addBodyLine("}");
    }

}
