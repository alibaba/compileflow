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
package com.alibaba.compileflow.engine.process.preruntime.generator.impl.action.support;

import com.alibaba.compileflow.engine.definition.common.action.IAction;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.process.preruntime.generator.impl.action.AbstractScriptActionGenerator;
import com.alibaba.compileflow.engine.process.preruntime.generator.script.ScriptExecutorProvider;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;
import com.ql.util.express.DefaultContext;
import com.ql.util.express.IExpressContext;

/**
 * @author wuxiang
 * @author yusu
 */
public class QLActionGenerator extends AbstractScriptActionGenerator {

    public QLActionGenerator(AbstractProcessRuntime runtime,
                             IAction action) {
        super(runtime, action);
    }

    @Override
    public String getActionType() {
        return "QL";
    }

    @Override
    protected String getScriptExecutorName() {
        return "QL";
    }

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
        if (actionHandle == null) {
            codeTargetSupport.addBodyLine("//TODO");
        }

        addImportedType(codeTargetSupport, IExpressContext.class);
        addImportedType(codeTargetSupport, DefaultContext.class);
        addImportedType(codeTargetSupport, ScriptExecutorProvider.class);
        codeTargetSupport.addBodyLine("IExpressContext<String, Object> nfScriptContext = new DefaultContext<>();");
        generateScriptExecuteCode(codeTargetSupport);
    }

    @Override
    public String generateActionMethodName(CodeTargetSupport codeTargetSupport) {
        return "executeQL" + getExpression().hashCode();
    }

}
