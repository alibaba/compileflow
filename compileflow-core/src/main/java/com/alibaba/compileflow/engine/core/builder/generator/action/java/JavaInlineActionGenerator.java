package com.alibaba.compileflow.engine.core.builder.generator.action.java;

import com.alibaba.compileflow.engine.core.builder.generator.GeneratorContext;
import com.alibaba.compileflow.engine.core.builder.generator.action.AbstractActionGenerator;
import com.alibaba.compileflow.engine.core.builder.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.core.definition.action.IAction;
import com.alibaba.compileflow.engine.core.definition.action.impl.JavaInlineActionHandle;
import com.alibaba.compileflow.engine.core.definition.var.IVar;
import com.alibaba.compileflow.engine.core.infrastructure.ClassWrapper;
import com.alibaba.compileflow.engine.core.infrastructure.type.DataType;
import com.alibaba.compileflow.engine.core.infrastructure.utils.JavaIdentifierUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates code for java-inline actions (expr and block modes).
 *
 * @author yusu
 */
public class JavaInlineActionGenerator extends AbstractActionGenerator {

    public JavaInlineActionGenerator(GeneratorContext context, IAction action) {
        super(context, action);
    }

    @Override
    public String getActionType() {
        return "java-inline";
    }

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
        JavaInlineActionHandle handle = (JavaInlineActionHandle) this.actionHandle;

        List<String> imports = handle.getImports();
        if (CollectionUtils.isNotEmpty(imports)) {
            imports.stream().filter(StringUtils::isNotBlank)
                    .forEach(e -> getClassTarget(codeTargetSupport).addImportedType(ClassWrapper.of(e)));
        }

        String code = handle.getCode();
        if (StringUtils.isBlank(code)) {
            return;
        }

        codeTargetSupport.addBodyLine("{");
        generateParamCode(codeTargetSupport);

        JavaInlineActionHandle.Mode mode = handle.getMode();
        switch (mode) {
            case EXPR:
                generateExprCode(codeTargetSupport, code);
                break;
            case BLOCK:
                generateBlockCode(codeTargetSupport, code);
                break;
            default:
                throw new IllegalArgumentException("Unsupported java-inline mode: " + mode);
        }

        codeTargetSupport.addBodyLine("}");
    }


    private void generateParamCode(CodeTargetSupport codeTargetSupport) {
        Set<String> usedAliases = new HashSet<>();
        for (IVar v : actionHandle.getParamVars()) {
            String desired = StringUtils.defaultIfBlank(v.getName(), v.getContextVarName());
            String aliasBase = JavaIdentifierUtils.toFieldName(StringUtils.defaultIfBlank(desired, "p"));
            String alias = aliasBase;
            int suffix = 2;
            while (usedAliases.contains(alias)) {
                alias = aliasBase + suffix++;
            }
            usedAliases.add(alias);

            String ctx = StringUtils.defaultIfBlank(v.getContextVarName(), alias);
            if (!alias.equals(ctx)) {
                Class<?> clazz = DataType.getJavaClass(v.getDataType());
                codeTargetSupport.addBodyLine(clazz.getName() + " " + alias + " = this." + ctx + ";");
            }
        }
    }

    private void generateExprCode(CodeTargetSupport codeTargetSupport, String expr) {
        String trimmed = expr.trim();

        IVar ret = getReturnVar();
        if (ret != null && StringUtils.isNotBlank(ret.getContextVarName())) {
            Class<?> returnClass = DataType.getJavaClass(ret.getDataType());
            String castType = returnClass.getName();
            codeTargetSupport.addBodyLine(getReturnVarCode() + "(" + castType + ")(" + trimmed + ");");
        } else {
            if (trimmed.endsWith(";")) {
                codeTargetSupport.addBodyLine(trimmed);
            } else {
                codeTargetSupport.addBodyLine(trimmed + ";");
            }
        }
    }

    private void generateBlockCode(CodeTargetSupport codeTargetSupport, String block) {
        codeTargetSupport.addBodyLine(block);
    }

    @Override
    public String generateActionMethodName(CodeTargetSupport codeTargetSupport) {
        String code = ((JavaInlineActionHandle) actionHandle).getCode();
        String hash = code == null ? "0" : String.valueOf(Integer.toUnsignedLong(code.hashCode()));
        return "inline_" + "_" + hash;
    }

}
