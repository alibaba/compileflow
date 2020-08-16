package com.alibaba.compileflow.engine.process.preruntime.generator.impl.tbbpm;

import com.alibaba.compileflow.engine.definition.tbbpm.NoteNode;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;

/**
 * @author yusu
 * @date 2020/08/05
 */
public class NoteGenerator extends AbstractTbbpmNodeGenerator<NoteNode> {

    public NoteGenerator(AbstractProcessRuntime runtime,
                         NoteNode flowNode) {
        super(runtime, flowNode);
    }

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {

    }

}
