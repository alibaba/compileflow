package com.alibaba.compileflow.engine.runtime.impl;

import com.alibaba.compileflow.engine.definition.common.NodeContainer;
import com.alibaba.compileflow.engine.definition.common.TransitionNode;
import com.alibaba.compileflow.engine.definition.tbbpm.*;
import com.alibaba.compileflow.engine.process.builder.generator.code.ClassTarget;
import com.alibaba.compileflow.engine.process.builder.generator.impl.tbbpm.*;
import com.alibaba.compileflow.engine.process.builder.generator.provider.NodeGeneratorProvider;
import com.alibaba.compileflow.engine.process.builder.generator.provider.impl.TbbpmNodeGeneratorProvider;

/**
 * @author yusu
 */
public class TbbpmProcessCodeGenerator extends AbstractProcessCodeGenerator<TbbpmModel, TbbpmProcessRuntime> {

    public TbbpmProcessCodeGenerator(TbbpmProcessRuntime runtime) {
        super(runtime);
    }

    @Override
    protected String generateGetEngineCode() {
        return "ProcessEngine engine = ProcessEngineFactory.getProcessEngine();";
    }

    @Override
    protected void addImportedType(ClassTarget testClassTarget) {

    }

    @Override
    protected NodeGeneratorProvider buildNodeGeneratorProvider() {
        return new TbbpmNodeGeneratorProvider(runtime);
    }

    public void registerNodeGenerator(NodeContainer<TransitionNode> nodeContainer) {
        if (nodeContainer == null) {
            throw new IllegalArgumentException("NodeContainer cannot be null");
        }
        for (TransitionNode node : nodeContainer.getAllNodes()) {

            if (node instanceof AutoTaskNode) {
                registerGenerator(node, new AutoTaskGenerator(runtime, (AutoTaskNode) node));
            } else if (node instanceof ScriptTaskNode) {
                registerGenerator(node, new com.alibaba.compileflow.engine.process.builder.generator.impl.tbbpm.ScriptTaskGenerator(runtime, (ScriptTaskNode) node));
            } else if (node instanceof DecisionNode) {
                registerGenerator(node, new DecisionGenerator(runtime, (DecisionNode) node));
            } else if (node instanceof StartNode) {
                registerGenerator(node, new StartGenerator(runtime, (StartNode) node));
            } else if (node instanceof EndNode) {
                registerGenerator(node, new EndGenerator(runtime, (EndNode) node));
            } else if (node instanceof LoopProcessNode) {
                registerGenerator(node, new LoopProcessGenerator(runtime, (LoopProcessNode) node));
            } else if (node instanceof BreakNode) {
                registerGenerator(node, new BreakGenerator(runtime, (BreakNode) node));
            } else if (node instanceof ContinueNode) {
                registerGenerator(node, new ContinueGenerator(runtime, (ContinueNode) node));
            } else if (node instanceof SubBpmNode) {
                registerGenerator(node, new SubBpmGenerator(runtime, (SubBpmNode) node));
            } else if (node instanceof NoteNode) {
                registerGenerator(node, new NoteGenerator(runtime, (NoteNode) node));
            } else if (node instanceof WaitTaskNode) {
                registerGenerator(node, new WaitTaskGenerator(runtime, (WaitTaskNode) node));
            } else if (node instanceof WaitEventTaskNode) {
                registerGenerator(node, new WaitEventTaskGenerator(runtime, (WaitEventTaskNode) node));
            } else {
                throw new IllegalStateException("Unknown node type: " + node.getClass().getName());
            }

            if (node instanceof NodeContainer) {
                registerNodeGenerator((NodeContainer) node);
            }
        }
    }

}
