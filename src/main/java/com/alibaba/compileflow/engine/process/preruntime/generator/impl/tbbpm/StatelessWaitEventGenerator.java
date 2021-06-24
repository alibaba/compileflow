package com.alibaba.compileflow.engine.process.preruntime.generator.impl.tbbpm;

import com.alibaba.compileflow.engine.definition.tbbpm.WaitEventNode;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;

import java.util.List;

/**
 * process生成代码时使用
 *
 * @author wuxiang
 * since 2021/6/23
 **/
public class StatelessWaitEventGenerator extends AbstractTbbpmInOutActionNodeGenerator<WaitEventNode> {

    public StatelessWaitEventGenerator(AbstractProcessRuntime runtime,
                                       WaitEventNode flowNode) {
        super(runtime, flowNode);
    }

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
        generateNodeComment(codeTargetSupport);
        codeTargetSupport.addBodyLine("if (true) {");
        List<String> returnVarLines = runtime.wrapReturnVarLines();
        codeTargetSupport.addBodyLines(returnVarLines);
        codeTargetSupport.addBodyLine("return _pResult ;");
        codeTargetSupport.addBodyLine("} ");
    }

}
