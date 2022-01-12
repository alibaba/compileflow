package com.alibaba.compileflow.engine.runtime.impl;


import com.alibaba.compileflow.engine.common.constant.FlowModelType;
import com.alibaba.compileflow.engine.definition.bpmn.*;
import com.alibaba.compileflow.engine.definition.common.NodeContainer;
import com.alibaba.compileflow.engine.definition.common.TransitionNode;
import com.alibaba.compileflow.engine.process.preruntime.generator.factory.GeneratorProviderFactory;
import com.alibaba.compileflow.engine.process.preruntime.generator.impl.bpmn.*;
import com.alibaba.compileflow.engine.process.preruntime.generator.provider.impl.BpmnNodeGeneratorProvider;

/**
 * @author wuxiang
 * @author yusu
 */
public class BpmnProcessRuntime extends AbstractProcessRuntime<BpmnModel> {

    private BpmnProcessRuntime(BpmnModel bpmnModel) {
        super(bpmnModel);
    }

    public static BpmnProcessRuntime of(BpmnModel bpmnModel) {
        return new BpmnProcessRuntime(bpmnModel);
    }

    @Override
    public FlowModelType getFlowModelType() {
        return FlowModelType.BPMN;
    }

    @Override
    protected void registerNodeGenerator(NodeContainer<TransitionNode> nodeContainer) {
        for (TransitionNode node : nodeContainer.getAllNodes()) {
            if (node instanceof ServiceTask) {
                registerGenerator(node, new ServiceTaskGenerator(this, (ServiceTask) node));
            } else if (node instanceof ScriptTask) {
                registerGenerator(node, new ScriptTaskGenerator(this, (ScriptTask) node));
            } else if (node instanceof ExclusiveGateway) {
                registerGenerator(node, new StatefulExclusiveGatewayGenerator(this, (ExclusiveGateway) node));
            } else if (node instanceof StartEvent) {
                registerGenerator(node, new StartEventGenerator(this, (StartEvent) node));
            } else if (node instanceof EndEvent) {
                registerGenerator(node, new StatefulEndEventGenerator(this, (EndEvent) node));
            } else if (node instanceof SubProcess) {
                registerGenerator(node, new SubProcessGenerator(this, (SubProcess) node));
            } else if (node instanceof ReceiveTask) {
                registerGenerator(node, new ReceiveTaskGenerator(this, (ReceiveTask) node));
            }

            if (node instanceof NodeContainer) {
                registerNodeGenerator((NodeContainer) node);
            }
        }
    }

    @Override
    protected boolean isBpmn20() {
        return false;
    }

    @Override
    protected GeneratorProviderFactory getGeneratorProviderFactory() {
        return () -> new BpmnNodeGeneratorProvider(this);
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
