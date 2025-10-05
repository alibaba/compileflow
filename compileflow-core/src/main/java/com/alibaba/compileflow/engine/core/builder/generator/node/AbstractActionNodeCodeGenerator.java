/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.core.builder.generator.node;

import com.alibaba.compileflow.engine.core.builder.generator.CodeGenerator;
import com.alibaba.compileflow.engine.core.builder.generator.GeneratorContext;
import com.alibaba.compileflow.engine.core.builder.generator.GeneratorFactory;
import com.alibaba.compileflow.engine.core.builder.generator.action.ActionMethodGenerator;
import com.alibaba.compileflow.engine.core.builder.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.core.definition.Node;
import com.alibaba.compileflow.engine.core.definition.action.HasAction;
import com.alibaba.compileflow.engine.core.definition.action.IAction;
import com.alibaba.compileflow.engine.core.definition.job.HasJob;
import com.alibaba.compileflow.engine.core.definition.job.JobPolicyElement;
import com.alibaba.compileflow.engine.core.definition.var.IVar;
import com.alibaba.compileflow.engine.core.infrastructure.utils.JavaIdentifierUtils;
import com.alibaba.compileflow.engine.core.runtime.executor.policy.ExecutionPolicy;
import org.apache.commons.lang3.StringUtils;

import java.util.stream.Collectors;

import static com.alibaba.compileflow.engine.core.builder.generator.action.AbstractActionGenerator.GENERATE_ACTION_METHOD_WITH_RETURN_VAR;

/**
 * Abstract base class for action node code generators.
 * Provides functionality for generating code from nodes that have actions,
 * including action execution and job policy handling.
 *
 * @param <N> The type of action node this generator handles
 * @author yusu
 */
public abstract class AbstractActionNodeCodeGenerator<N extends Node>
        extends AbstractNodeCodeGenerator<N> {

    public AbstractActionNodeCodeGenerator(GeneratorContext context, N flowNode) {
        super(context, flowNode);
    }

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
        HasAction hasAction = (HasAction) flowNode;
        IAction action = hasAction.getAction();
        generateActionCode(codeTargetSupport, action);
    }

    protected void generateActionCode(CodeTargetSupport codeTargetSupport, IAction action) {
        if (action != null && action.getActionHandle() != null) {
            CodeGenerator actionGenerator = GeneratorFactory.getInstance().getActionGenerator(action, context);
            actionGenerator.generateCode(codeTargetSupport);
        }
    }

    protected void generateActionMethodCode(CodeTargetSupport codeTargetSupport, IAction action) {
        if (action != null) {
            ActionMethodGenerator actionMethodGenerator = GeneratorFactory.getInstance()
                    .getActionGenerator(action, context);
            String methodName = actionMethodGenerator.generateActionMethodName(codeTargetSupport)
                    + JavaIdentifierUtils.toMethodSuffix(flowNode.getId());
            actionMethodGenerator.generateActionMethodCode(methodName, codeTargetSupport);

            String params = action.getActionHandle().getParamVars()
                    .stream().map(IVar::getContextVarName)
                    .collect(Collectors.joining(", "));
            if (flowNode instanceof HasJob && ((HasJob) flowNode).getJobPolicy() != null) {
                String policyExpr = buildJobPolicyLiteral(((HasJob) flowNode).getJobPolicy());

                String callExpr = methodName + "(" + params + ")";
                String nodeId = flowNode.getId();
                String exec;
                if (GENERATE_ACTION_METHOD_WITH_RETURN_VAR) {
                    exec = getReturnVarCode(action)
                            + "ActionExecutor.call(\"" + nodeId + "\", () -> " + callExpr + ", " + policyExpr + ")";
                } else {
                    exec = "ActionExecutor.run(\"" + nodeId + "\", () -> " + callExpr + ", " + policyExpr + ")";
                }

                codeTargetSupport.addBodyLine(exec + ";");
            } else {
                codeTargetSupport.addBodyLine((GENERATE_ACTION_METHOD_WITH_RETURN_VAR ? getReturnVarCode(action) : "")
                        + methodName
                        + "(" + params + ");");

            }
        }
    }

    private String getReturnVarCode(IAction action) {
        IVar returnVar = action.getActionHandle().getReturnVar();
        if (returnVar == null || returnVar.getContextVarName() == null) {
            return "";
        }
        return returnVar.getContextVarName() + " = ";
    }

    private String buildJobPolicyLiteral(JobPolicyElement jobPolicy) {
        String timeout = StringUtils.defaultIfBlank(jobPolicy.getTimeout(), "PT0S");
        String retry = StringUtils.defaultIfBlank(jobPolicy.getRetry(), "R0/PT0S");
        String retryOn = StringUtils.defaultIfBlank(jobPolicy.getRetryOn(), "never");
        String onFailure = StringUtils.defaultIfBlank(jobPolicy.getOnFailure(), "incident");

        ExecutionPolicy executionPolicy = ExecutionPolicy.of(timeout, retry, retryOn, onFailure);
        return "ExecutionPolicy.of(" + executionPolicy.getTimeoutMs() + "L, "
                + executionPolicy.getRetryCount()
                + ", " + executionPolicy.getRetryIntervalMs() + "L, \""
                + (executionPolicy.getRetryOn()) + "\", \""
                + escape(executionPolicy.getOnFailure()) + "\")";
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

}
