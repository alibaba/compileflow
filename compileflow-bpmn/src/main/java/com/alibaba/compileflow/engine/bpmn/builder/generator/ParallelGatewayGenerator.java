package com.alibaba.compileflow.engine.bpmn.builder.generator;

import com.alibaba.compileflow.engine.bpmn.definition.ParallelGateway;
import com.alibaba.compileflow.engine.bpmn.definition.SequenceFlow;
import com.alibaba.compileflow.engine.core.builder.generator.GeneratorContext;
import com.alibaba.compileflow.engine.core.builder.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.core.builder.generator.node.AbstractGatewayNodeCodeGenerator;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

/**
 * ParallelGateway node code generator for BPMN.
 *
 * @author yusu
 */
public class ParallelGatewayGenerator extends AbstractGatewayNodeCodeGenerator<ParallelGateway> {

    public ParallelGatewayGenerator(GeneratorContext context, ParallelGateway flowNode) {
        super(context, flowNode);
    }

    @Override
    protected void generateBranchNodeCode(CodeTargetSupport codeTargetSupport) {
        final List<SequenceFlow> outgoingFlows = flowNode.getOutgoingTransitions();
        if (CollectionUtils.isEmpty(outgoingFlows)) {
            return;
        }

        generateParallelBranchLogic(codeTargetSupport, outgoingFlows);
    }

}
