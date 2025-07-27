package com.alibaba.compileflow.engine.runtime.impl;

import com.alibaba.compileflow.engine.common.ClassWrapper;
import com.alibaba.compileflow.engine.definition.bpmn.*;
import com.alibaba.compileflow.engine.definition.common.NodeContainer;
import com.alibaba.compileflow.engine.definition.common.TransitionNode;
import com.alibaba.compileflow.engine.process.builder.generator.code.ClassTarget;
import com.alibaba.compileflow.engine.process.builder.generator.impl.bpmn.*;
import com.alibaba.compileflow.engine.process.builder.generator.provider.NodeGeneratorProvider;
import com.alibaba.compileflow.engine.process.builder.generator.provider.impl.BpmnNodeGeneratorProvider;

/**
 * @author yusu
 */
public class BpmnProcessCodeGenerator extends AbstractProcessCodeGenerator<BpmnModel, BpmnProcessRuntime> {

    public BpmnProcessCodeGenerator(BpmnProcessRuntime runtime) {
        super(runtime);
    }

    @Override
    protected String generateGetEngineCode() {
        return "ProcessEngine<BpmnModel> engine = ProcessEngineFactory.getProcessEngine(FlowModelType.BPMN);";
    }

    @Override
    protected void addImportedType(ClassTarget classTarget) {
        classTarget.addImportedType(ClassWrapper.of("com.alibaba.compileflow.engine.definition.bpmn.BpmnModel"));
    }

    @Override
    protected NodeGeneratorProvider buildNodeGeneratorProvider() {
        return new BpmnNodeGeneratorProvider(runtime);
    }

    protected void registerNodeGenerator(NodeContainer<TransitionNode> nodeContainer) {
        for (TransitionNode node : nodeContainer.getAllNodes()) {
            if (node instanceof ServiceTask) {
                registerGenerator(node, new ServiceTaskGenerator(runtime, (ServiceTask) node));
            } else if (node instanceof ScriptTask) {
                registerGenerator(node, new ScriptTaskGenerator(runtime, (ScriptTask) node));
            } else if (node instanceof ExclusiveGateway) {
                registerGenerator(node, new ExclusiveGatewayGenerator(runtime, (ExclusiveGateway) node));
            } else if (node instanceof StartEvent) {
                registerGenerator(node, new StartEventGenerator(runtime, (StartEvent) node));
            } else if (node instanceof EndEvent) {
                registerGenerator(node, new EndEventGenerator(runtime, (EndEvent) node));
            } else if (node instanceof SubProcess) {
                registerGenerator(node, new SubProcessGenerator(runtime, (SubProcess) node));
            } else if (node instanceof ReceiveTask) {
                registerGenerator(node, new ReceiveTaskGenerator(runtime, (ReceiveTask) node));
            } else if (node instanceof ParallelGateway) {
                registerGenerator(node, new ParallelGatewayGenerator(runtime, (ParallelGateway) node));
            }

            if (node instanceof NodeContainer) {
                registerNodeGenerator((NodeContainer) node);
            }
        }
    }

}
