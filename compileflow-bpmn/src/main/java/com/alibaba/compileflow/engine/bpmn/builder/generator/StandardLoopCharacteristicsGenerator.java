package com.alibaba.compileflow.engine.bpmn.builder.generator;

import com.alibaba.compileflow.engine.bpmn.definition.StandardLoopCharacteristics;
import com.alibaba.compileflow.engine.core.builder.generator.AbstractWrapperCodeGenerator;
import com.alibaba.compileflow.engine.core.builder.generator.CodeGenerator;
import com.alibaba.compileflow.engine.core.builder.generator.GeneratorContext;
import com.alibaba.compileflow.engine.core.builder.generator.code.CodeTargetSupport;
import org.apache.commons.lang3.StringUtils;

/**
 * Generates while/do-while loop code blocks.
 *
 * @author yusu
 */
public class StandardLoopCharacteristicsGenerator extends AbstractWrapperCodeGenerator {

    private final StandardLoopCharacteristics loopCharacteristics;

    public StandardLoopCharacteristicsGenerator(GeneratorContext context,
                                                StandardLoopCharacteristics loopCharacteristics,
                                                CodeGenerator generator) {
        super(context, generator);
        this.loopCharacteristics = loopCharacteristics;
    }

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
        String loopCondition = loopCharacteristics.getLoopCondition();
        Long loopMaximum = loopCharacteristics.getLoopMaximum();
        Boolean testBefore = loopCharacteristics.getTestBefore();
        codeTargetSupport.addBodyLine("{");
        codeTargetSupport.addBodyLine("int _loopCount = 0;");
        String maxCond = loopMaximum != null ? " && _loopCount < " + loopMaximum : "";
        if (Boolean.TRUE.equals(testBefore)) {
            codeTargetSupport.addBodyLine("while (" + (StringUtils.isNotEmpty(loopCondition) ? loopCondition : "true") + maxCond + ") {");
        } else {
            codeTargetSupport.addBodyLine("do {");
        }
        generator.generateCode(codeTargetSupport);
        codeTargetSupport.addBodyLine("_loopCount++;");
        if (!Boolean.TRUE.equals(testBefore)) {
            codeTargetSupport.addBodyLine("} while (" + (StringUtils.isNotEmpty(loopCondition) ? loopCondition : "true") + maxCond + ");");
        } else {
            codeTargetSupport.addBodyLine("}");
        }
        codeTargetSupport.addBodyLine("}");
    }
}
