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
package com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.provider.support;

import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.support.bpmn.*;

/**
 * @author wuxiang
 * @author yusu
 */
public class BpmnElementParserProvider extends AbstractFlowElementParserProvider {

    private static volatile BpmnElementParserProvider bpmnElementParserProvider;

    public static BpmnElementParserProvider getInstance() {
        if (bpmnElementParserProvider == null) {
            synchronized (BpmnElementParserProvider.class) {
                if (bpmnElementParserProvider == null) {
                    bpmnElementParserProvider = new BpmnElementParserProvider();
                    bpmnElementParserProvider.init();
                }
            }
        }
        return bpmnElementParserProvider;
    }

    public void init() {
        registerParser(new DefinitionsParser());
        registerParser(new ProcessParser());
        registerParser(new ExtensionElementsParser());
        registerParser(new StartEventParser());
        registerParser(new EndEventParser());
        registerParser(new ServiceTaskParser());
        registerParser(new ScriptTaskParser());
        registerParser(new ScriptParser());
        registerParser(new UserTaskParser());
        registerParser(new ReceiveTaskParser());
        registerParser(new CallActivityParser());
        registerParser(new ParallelGatewayParser());
        registerParser(new ExclusiveGatewayParser());
        registerParser(new InclusiveGatewayParser());
        registerParser(new SubProcessParser());
        registerParser(new SignalParser());
        registerParser(new SequenceFlowParser());
        registerParser(new ConditionExpressionParser());
        registerParser(new StandardLoopCharacteristicsParser());
        registerParser(new MultiInstanceLoopCharacteristicsParser());
    }

}