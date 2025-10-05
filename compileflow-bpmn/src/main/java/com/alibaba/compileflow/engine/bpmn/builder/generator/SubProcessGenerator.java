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
package com.alibaba.compileflow.engine.bpmn.builder.generator;

import com.alibaba.compileflow.engine.bpmn.definition.FlowNode;
import com.alibaba.compileflow.engine.bpmn.definition.SubProcess;
import com.alibaba.compileflow.engine.core.builder.generator.CodeGenerator;
import com.alibaba.compileflow.engine.core.builder.generator.GeneratorContext;
import com.alibaba.compileflow.engine.core.builder.generator.GeneratorFactory;
import com.alibaba.compileflow.engine.core.builder.generator.code.CodeTargetSupport;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

/**
 * Generator for BPMN SubProcess elements.
 * <p>
 * SubProcess in BPMN represents an embedded sub-process that contains its own flow elements.
 * Unlike CallActivity, SubProcess should generate inline code rather than calling external processes.
 * This aligns with BPMN 2.0 semantics where SubProcess is expanded within the parent process.
 *
 * <p><b>Implementation Strategy:</b></p>
 * <ul>
 *   <li><b>Embedded SubProcess:</b> When flowElements are present, generate inline code for contained nodes</li>
 * </ul>
 *
 * @author yusu
 */
public class SubProcessGenerator extends AbstractBpmnNodeGenerator<SubProcess> {

    public SubProcessGenerator(GeneratorContext context, SubProcess flowNode) {
        super(context, flowNode);
    }

    @Override
    public void generateCode(CodeTargetSupport codeTargetSupport) {
        generateNodeComment(codeTargetSupport);

        List<FlowNode> subProcessNodes = flowNode.getAllNodes();
        if (CollectionUtils.isNotEmpty(subProcessNodes)) {
            generateEmbeddedSubProcess(codeTargetSupport);
        }
    }

    private void generateEmbeddedSubProcess(CodeTargetSupport codeTargetSupport) {
        codeTargetSupport.addBodyLine("{");
        CodeGenerator containerGenerator = GeneratorFactory.getInstance().getContainerGenerator(flowNode, context);
        containerGenerator.generateCode(codeTargetSupport);
        codeTargetSupport.addBodyLine("}");
    }

}
