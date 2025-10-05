package com.alibaba.compileflow.engine.tbbpm.builder.generator;

import com.alibaba.compileflow.engine.core.builder.generator.GeneratorContext;
import com.alibaba.compileflow.engine.core.builder.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.core.builder.generator.node.AbstractGatewayNodeCodeGenerator;
import com.alibaba.compileflow.engine.tbbpm.definition.ParallelNode;
import com.alibaba.compileflow.engine.tbbpm.definition.Transition;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

/**
 * Parallel node code generator.
 *
 * @author yusu
 */
public class ParallelGenerator extends AbstractGatewayNodeCodeGenerator<ParallelNode> {

    public ParallelGenerator(GeneratorContext context, ParallelNode flowNode) {
        super(context, flowNode);
    }

    @Override
    protected void generateBranchNodeCode(CodeTargetSupport codeTargetSupport) {
        final List<Transition> outgoingFlows = flowNode.getOutgoingTransitions();
        if (CollectionUtils.isEmpty(outgoingFlows)) {
            return;
        }

        generateParallelBranchLogic(codeTargetSupport, outgoingFlows);
    }

}
