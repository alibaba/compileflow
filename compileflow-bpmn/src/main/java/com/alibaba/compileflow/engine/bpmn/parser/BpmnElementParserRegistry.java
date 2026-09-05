/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.bpmn.parser;

import com.alibaba.compileflow.engine.bpmn.model.BpmnModelConstants;
import com.alibaba.compileflow.engine.bpmn.model.TimerValue;
import com.alibaba.compileflow.engine.core.xml.parser.AbstractFlowElementParserRegistry;
import java.util.List;

/**
 * Registers BPMN element parsers for the executable CompileFlow subset.
 *
 * @author yusu
 */
final class BpmnElementParserRegistry extends AbstractFlowElementParserRegistry {
    private static final BpmnElementParserRegistry INSTANCE = new BpmnElementParserRegistry();

    private BpmnElementParserRegistry() {
        super(List.of(new DefinitionsParser(), new ProcessParser(), new ExtensionElementsParser(),
                new StartEventParser(), new EndEventParser(), new ServiceTaskParser(), new ScriptTaskParser(),
                new ScriptParser(), new ReceiveTaskParser(), new IntermediateCatchEventParser(),
                new MessageEventDefinitionParser(), new TimerEventDefinitionParser(),
                new TimerValueParser(BpmnModelConstants.BPMN_ELEMENT_TIME_DURATION, TimerValue.Kind.DURATION),
                new TimerValueParser(BpmnModelConstants.BPMN_ELEMENT_TIME_DATE, TimerValue.Kind.DATE),
                new TimerValueParser(BpmnModelConstants.BPMN_ELEMENT_TIME_CYCLE, TimerValue.Kind.CYCLE),
                new CallActivityParser(), new ParallelGatewayParser(), new ExclusiveGatewayParser(),
                new InclusiveGatewayParser(), new SubProcessParser(), new MessageParser(), new SequenceFlowParser(),
                new SkippedMetadataElementParser(BpmnModelConstants.BPMN_ELEMENT_DOCUMENTATION,
                        BpmnModelConstants.BPMN20_NS),
                new SkippedMetadataElementParser(BpmnModelConstants.BPMNDI_ELEMENT_BPMN_DIAGRAM,
                        BpmnModelConstants.BPMNDI_NS),
                new IgnoredTextElementParser(BpmnModelConstants.BPMN_ELEMENT_INCOMING),
                new IgnoredTextElementParser(BpmnModelConstants.BPMN_ELEMENT_OUTGOING), new ConditionExpressionParser(),
                new MultiInstanceLoopCharacteristicsParser(), new StandardLoopCharacteristicsParser(),
                new LoopConditionParser()));
    }

    static BpmnElementParserRegistry getInstance() {
        return INSTANCE;
    }
}
