package com.alibaba.compileflow.engine.runtime.impl;

import com.alibaba.compileflow.engine.common.constants.FlowModelType;
import com.alibaba.compileflow.engine.definition.common.NodeContainer;
import com.alibaba.compileflow.engine.definition.common.TransitionNode;
import com.alibaba.compileflow.engine.definition.tbbpm.*;
import com.alibaba.compileflow.engine.process.preruntime.generator.factory.GeneratorProviderFactory;
import com.alibaba.compileflow.engine.process.preruntime.generator.impl.tbbpm.*;
import com.alibaba.compileflow.engine.process.preruntime.generator.provider.impl.TbbpmNodeGeneratorProvider;

/**
 * @author wuxiang
 * @author yusu
 */
public class TbbpmStatefulProcessRuntime extends AbstractStatefulProcessRuntime<TbbpmModel> {

    private TbbpmStatefulProcessRuntime(TbbpmModel tbbpmModel) {
        super(tbbpmModel);
    }

    public static TbbpmStatefulProcessRuntime of(TbbpmModel tbbpmModel) {
        return new TbbpmStatefulProcessRuntime(tbbpmModel);
    }

    @Override
    public FlowModelType getFlowModelType() {
        return FlowModelType.TBBPM;
    }

    @Override
    protected boolean isBpmn20() {
        return false;
    }

    @Override
    protected void registerNodeGenerator(NodeContainer<TransitionNode> nodeContainer) {
        for (TransitionNode node : nodeContainer.getAllNodes()) {
            if (node instanceof AutoTaskNode) {
                registerGenerator(node, new StatefulAutoTaskGenerator(this, (AutoTaskNode) node));
            } else if (node instanceof ScriptTaskNode) {
                registerGenerator(node, new ScriptTaskGenerator(this, (ScriptTaskNode) node));
            } else if (node instanceof DecisionNode) {
                registerGenerator(node, new StatefulDecisionGenerator(this, (DecisionNode) node));
            } else if (node instanceof StartNode) {
                registerGenerator(node, new StartGenerator(this, (StartNode) node));
            } else if (node instanceof EndNode) {
                registerGenerator(node, new StatefulEndGenerator(this, (EndNode) node));
            } else if (node instanceof LoopProcessNode) {
                registerGenerator(node, new LoopProcessGenerator(this, (LoopProcessNode) node));
            } else if (node instanceof BreakNode) {
                registerGenerator(node, new BreakGenerator(this, (BreakNode) node));
            } else if (node instanceof ContinueNode) {
                registerGenerator(node, new ContinueGenerator(this, (ContinueNode) node));
            } else if (node instanceof SubBpmNode) {
                registerGenerator(node, new StatefulSubBpmGenerator(this, (SubBpmNode) node));
            } else if (node instanceof WaitTaskNode) {
                registerGenerator(node, new WaitTaskGenerator(this, (WaitTaskNode) node));
            } else if (node instanceof WaitEventNode) {
                registerGenerator(node, new WaitEventGenerator(this, (WaitEventNode) node));
            }

            if (node instanceof NodeContainer) {
                registerNodeGenerator((NodeContainer) node);
            }
        }
    }

    @Override
    protected GeneratorProviderFactory getGeneratorProviderFactory() {
        return () -> new TbbpmNodeGeneratorProvider(this);
    }

    @Override
    public void init() {
        super.init();
    }

    @Override
    public void stop() {
        super.stop();
    }

}
