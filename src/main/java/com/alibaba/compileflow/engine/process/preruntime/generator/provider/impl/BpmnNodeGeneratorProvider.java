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
package com.alibaba.compileflow.engine.process.preruntime.generator.provider.impl;

import com.alibaba.compileflow.engine.definition.bpmn.Activity;
import com.alibaba.compileflow.engine.definition.bpmn.LoopCharacteristics;
import com.alibaba.compileflow.engine.definition.bpmn.StandardLoopCharacteristics;
import com.alibaba.compileflow.engine.definition.common.Node;
import com.alibaba.compileflow.engine.process.preruntime.generator.Generator;
import com.alibaba.compileflow.engine.process.preruntime.generator.impl.bpmn.StandardLoopCharacteristicsGenerator;
import com.alibaba.compileflow.engine.process.preruntime.generator.provider.AbstractNodeGeneratorProvider;
import com.alibaba.compileflow.engine.runtime.impl.AbstractProcessRuntime;

/**
 * @author yusu
 */
public class BpmnNodeGeneratorProvider extends AbstractNodeGeneratorProvider {

    public BpmnNodeGeneratorProvider(AbstractProcessRuntime runtime) {
        super(runtime);
    }

    @Override
    protected Generator preProcess(Node node, Generator generator) {
        if (node instanceof Activity) {
            LoopCharacteristics loopCharacteristics = ((Activity)node).getLoopCharacteristics();
            if (loopCharacteristics instanceof StandardLoopCharacteristics) {
                return new StandardLoopCharacteristicsGenerator(runtime,
                    (StandardLoopCharacteristics)loopCharacteristics, generator);
            }
        }
        return generator;
    }

}
