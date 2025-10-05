package com.alibaba.compileflow.engine.tbbpm.builder.generator;

import com.alibaba.compileflow.engine.core.builder.generator.GeneratorContext;
import com.alibaba.compileflow.engine.core.builder.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.tbbpm.definition.NoteNode;

/**
 * @author yusu
 * @date 2020/08/05
 */
public class NoteGenerator extends AbstractTbbpmNodeGenerator<NoteNode> {

    public NoteGenerator(GeneratorContext context,
                         NoteNode flowNode) {
        super(context, flowNode);
    }

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {

    }

}
