package com.alibaba.compileflow.engine.process.builder.generator.impl.bpmn;

import com.alibaba.compileflow.engine.definition.bpmn.StandardLoopCharacteristics;
import com.alibaba.compileflow.engine.process.builder.generator.Generator;
import com.alibaba.compileflow.engine.process.builder.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.process.builder.generator.impl.AbstractWrapperGenerator;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;
import org.apache.commons.lang3.StringUtils;

/**
 * @author yusu
 * 标准循环（while/do-while）代码生成器
 */
public class StandardLoopCharacteristicsGenerator extends AbstractWrapperGenerator {

    private final StandardLoopCharacteristics loopCharacteristics;

    public StandardLoopCharacteristicsGenerator(AbstractProcessRuntime runtime,
                                                StandardLoopCharacteristics loopCharacteristics,
                                                Generator generator) {
        super(runtime, generator);
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
            // while型
            codeTargetSupport.addBodyLine("while (" + (StringUtils.isNotEmpty(loopCondition) ? loopCondition : "true") + maxCond + ") {");
        } else {
            // do-while型
            codeTargetSupport.addBodyLine("do {");
        }
        generator.generateCode(codeTargetSupport);
        codeTargetSupport.addBodyLine("    _loopCount++;");
        if (!Boolean.TRUE.equals(testBefore)) {
            codeTargetSupport.addBodyLine("} while (" + (StringUtils.isNotEmpty(loopCondition) ? loopCondition : "true") + maxCond + ");");
        } else {
            codeTargetSupport.addBodyLine("}");
        }
        codeTargetSupport.addBodyLine("}");
    }
}
