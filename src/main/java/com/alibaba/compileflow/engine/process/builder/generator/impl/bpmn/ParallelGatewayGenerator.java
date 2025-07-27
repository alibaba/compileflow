package com.alibaba.compileflow.engine.process.builder.generator.impl.bpmn;

import com.alibaba.compileflow.engine.definition.bpmn.ParallelGateway;
import com.alibaba.compileflow.engine.definition.bpmn.SequenceFlow;
import com.alibaba.compileflow.engine.definition.common.TransitionNode;
import com.alibaba.compileflow.engine.process.builder.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

public class ParallelGatewayGenerator extends AbstractBpmnNodeGenerator<ParallelGateway> {

    public ParallelGatewayGenerator(AbstractProcessRuntime runtime, ParallelGateway flowNode) {
        super(runtime, flowNode);
    }

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
        List<SequenceFlow> outgoingFlows = flowNode.getOutgoingFlows();
        if (CollectionUtils.isNotEmpty(outgoingFlows)) {
            codeTargetSupport.addBodyLine("// 决策网关：同步for循环分支，所有分支顺序执行");
            codeTargetSupport.addBodyLine("Map<String, Object> _parallelResults = new HashMap<>();");
            int idx = 0;
            for (SequenceFlow flow : outgoingFlows) {
                String targetRef = flow.getTargetRef();
                TransitionNode targetNode = (TransitionNode) runtime.getNodeById(targetRef);
                codeTargetSupport.addBodyLine("// Parallel branch to " + targetRef);
                codeTargetSupport.addBodyLine("try {");
                codeTargetSupport.addIndent();
                codeTargetSupport.addBodyLine("    // 分支" + idx);
                runtime.getNodeGeneratorProvider().getGenerator(targetNode).generateCode(codeTargetSupport);
                codeTargetSupport.addBodyLine("    // _parallelResults.put(\"" + targetRef + "\", ...); // 可收集分支结果");
                codeTargetSupport.addBodyLine("} catch (Exception e) {");
                codeTargetSupport.addBodyLine("    // 分支异常不影响其它分支，可汇总异常");
                codeTargetSupport.addBodyLine("}");
                idx++;
            }
            codeTargetSupport.addBodyLine("// 汇聚点：所有分支执行完毕后继续");
            codeTargetSupport.addBodyLine("// 可选：使用CompletableFuture等多线程并发执行分支，提升性能");
            codeTargetSupport.addBodyLine("// List<CompletableFuture<?>> futures = ...; CompletableFuture.allOf(futures...).join();");
        }
        // 汇聚：同步场景下可直接 pass
    }
}
