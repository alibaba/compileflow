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
package com.alibaba.compileflow.engine.process.preruntime.generator.impl.tbbpm;

import com.alibaba.compileflow.engine.definition.tbbpm.BreakNode;
import com.alibaba.compileflow.engine.process.preruntime.generator.code.CodeTargetSupport;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;
import org.apache.commons.lang.StringUtils;

/**
 * @author pin
 * @author yusu
 */
public class BreakGenerator extends AbstractTbbpmNodeGenerator<BreakNode> {

    public BreakGenerator(AbstractProcessRuntime runtime,
                          BreakNode flowNode) {
        super(runtime, flowNode);
    }

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
        if (StringUtils.isNotEmpty(flowNode.getExpression())) {
            codeTargetSupport.addBodyLine("if (" + flowNode.getExpression() + ") {");
            codeTargetSupport.addBodyLine("break;");
            codeTargetSupport.addBodyLine("}");
        } else {
            codeTargetSupport.addBodyLine("break;");
        }
    }

}
