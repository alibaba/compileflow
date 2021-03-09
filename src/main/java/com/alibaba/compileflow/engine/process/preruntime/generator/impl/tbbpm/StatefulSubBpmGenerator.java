package com.alibaba.compileflow.engine.process.preruntime.generator.impl.tbbpm;

import com.alibaba.compileflow.engine.definition.tbbpm.SubBpmNode;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.ClassTarget;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.MethodTarget;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;
import org.apache.commons.lang3.StringUtils;

/**
 * @author wuxiang
 * @author yusu
 */
public class StatefulSubBpmGenerator extends SubBpmGenerator {

    private static final String SUB_BPM_METHOD_NAME_PREFIX = "subBpm";

    public StatefulSubBpmGenerator(AbstractProcessRuntime runtime,
                                   SubBpmNode flowNode) {
        super(runtime, flowNode);
    }

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
        generateNodeComment(codeTargetSupport);
        if (flowNode.isWaitForTrigger()) {
            codeTargetSupport.addBodyLine("if (trigger) {");
            codeTargetSupport.addBodyLine("run = false;");
            codeTargetSupport.addBodyLine("break;");
            codeTargetSupport.addBodyLine("}");
        }

        String subBpmMethodName = generateSubBpmMethodName(flowNode);
        generateSubBpmMethodCode(codeTargetSupport, subBpmMethodName);
        codeTargetSupport.addBodyLine(subBpmMethodName + "();");
    }

    private String generateSubBpmMethodName(SubBpmNode subBpmNode) {
        String subBpmCode = subBpmNode.getSubBpmCode();
        subBpmCode = subBpmCode.substring(subBpmCode.lastIndexOf(".") + 1);
        if (subBpmCode.chars().allMatch(c -> Character.isLetterOrDigit(c) || '_' == c)) {
            return SUB_BPM_METHOD_NAME_PREFIX + StringUtils.capitalize(subBpmCode);
        }
        return SUB_BPM_METHOD_NAME_PREFIX + Math.abs(subBpmCode.hashCode());
    }

    private void generateSubBpmMethodCode(CodeTargetSupport codeTargetSupport,
                                          String subBpmMethodName) {
        MethodTarget method = new MethodTarget();
        method.setClassTarget(getClassTarget(codeTargetSupport));
        method.setName(subBpmMethodName);
        ClassTarget classTarget = getClassTarget(codeTargetSupport);
        super.generateCode(method);
        classTarget.addMethod(method);
    }

}
