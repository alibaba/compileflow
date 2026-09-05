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

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.bpmn.model.BpmnModelConstants;
import com.alibaba.compileflow.engine.core.xml.parser.XmlSource;
import java.util.Map;
import java.util.Set;

/**
 * Defines the attributes that CompileFlow can execute and preserve for its BPMN subset.
 *
 * @author yusu
 */
final class BpmnAttributeContract {
    private static final Map<String, Set<String>> UNQUALIFIED_ATTRIBUTES = Map.ofEntries(Map.entry(BpmnModelConstants.BPMN_ELEMENT_DEFINITIONS,
                    Set.of(BpmnModelConstants.BPMN_ATTRIBUTE_ID, BpmnModelConstants.BPMN_ATTRIBUTE_TARGET_NAMESPACE,
                            BpmnModelConstants.BPMN_ATTRIBUTE_EXPORTER,
                            BpmnModelConstants.BPMN_ATTRIBUTE_EXPORTER_VERSION,
                            BpmnModelConstants.BPMN_ATTRIBUTE_TYPE_LANGUAGE,
                            BpmnModelConstants.BPMN_ATTRIBUTE_EXPRESSION_LANGUAGE)),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_PROCESS,
                    Set.of(BpmnModelConstants.BPMN_ATTRIBUTE_ID, BpmnModelConstants.BPMN_ATTRIBUTE_NAME,
                            BpmnModelConstants.BPMN_ATTRIBUTE_IS_EXECUTABLE)),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_EXTENSION_ELEMENTS, Set.of()),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_START_EVENT,
                    Set.of(BpmnModelConstants.BPMN_ATTRIBUTE_ID, BpmnModelConstants.BPMN_ATTRIBUTE_NAME,
                            BpmnModelConstants.BPMN_ATTRIBUTE_IS_INTERRUPTING)),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_END_EVENT, idAndName()),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_SERVICE_TASK, idAndName()),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_SCRIPT_TASK,
                    Set.of(BpmnModelConstants.BPMN_ATTRIBUTE_ID, BpmnModelConstants.BPMN_ATTRIBUTE_NAME,
                            BpmnModelConstants.BPMN_ATTRIBUTE_SCRIPT_FORMAT)),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_SCRIPT, Set.of()),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_RECEIVE_TASK,
                    Set.of(BpmnModelConstants.BPMN_ATTRIBUTE_ID, BpmnModelConstants.BPMN_ATTRIBUTE_NAME,
                            BpmnModelConstants.BPMN_ATTRIBUTE_MESSAGE_REF,
                            BpmnModelConstants.BPMN_ATTRIBUTE_IMPLEMENTATION,
                            BpmnModelConstants.BPMN_ATTRIBUTE_OPERATION_REF)),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_INTERMEDIATE_CATCH_EVENT, idAndName()),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_MESSAGE_EVENT_DEFINITION,
                    Set.of(BpmnModelConstants.BPMN_ATTRIBUTE_ID, BpmnModelConstants.BPMN_ATTRIBUTE_MESSAGE_REF)),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_TIMER_EVENT_DEFINITION,
                    Set.of(BpmnModelConstants.BPMN_ATTRIBUTE_ID)),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_TIME_DURATION, Set.of(BpmnModelConstants.BPMN_ATTRIBUTE_LANGUAGE)),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_TIME_DATE, Set.of(BpmnModelConstants.BPMN_ATTRIBUTE_LANGUAGE)),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_TIME_CYCLE, Set.of(BpmnModelConstants.BPMN_ATTRIBUTE_LANGUAGE)),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_CALL_ACTIVITY,
                    Set.of(BpmnModelConstants.BPMN_ATTRIBUTE_ID, BpmnModelConstants.BPMN_ATTRIBUTE_NAME,
                            BpmnModelConstants.BPMN_ATTRIBUTE_CALLED_ELEMENT)),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_PARALLEL_GATEWAY, idNameAndGatewayDirection()),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_EXCLUSIVE_GATEWAY, idNameDefaultAndGatewayDirection()),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_INCLUSIVE_GATEWAY, idNameDefaultAndGatewayDirection()),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_SUB_PROCESS,
                    Set.of(BpmnModelConstants.BPMN_ATTRIBUTE_ID, BpmnModelConstants.BPMN_ATTRIBUTE_NAME,
                            BpmnModelConstants.BPMN_ATTRIBUTE_TRIGGERED_BY_EVENT)),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_MESSAGE, idAndName()),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_SEQUENCE_FLOW,
                    Set.of(BpmnModelConstants.BPMN_ATTRIBUTE_ID, BpmnModelConstants.BPMN_ATTRIBUTE_NAME,
                            BpmnModelConstants.BPMN_ATTRIBUTE_SOURCE_REF, BpmnModelConstants.BPMN_ATTRIBUTE_TARGET_REF,
                            BpmnModelConstants.BPMN_ATTRIBUTE_IS_IMMEDIATE)),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_INCOMING, Set.of()),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_OUTGOING, Set.of()),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_CONDITION_EXPRESSION,
                    Set.of(BpmnModelConstants.BPMN_ATTRIBUTE_LANGUAGE)),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_MULTI_INSTANCE_LOOP_CHARACTERISTICS,
                    Set.of(BpmnModelConstants.BPMN_ATTRIBUTE_ID, BpmnModelConstants.BPMN_ATTRIBUTE_IS_SEQUENTIAL)),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_STANDARD_LOOP_CHARACTERISTICS,
                    Set.of(BpmnModelConstants.BPMN_ATTRIBUTE_ID, BpmnModelConstants.BPMN_ATTRIBUTE_TEST_BEFORE,
                            BpmnModelConstants.BPMN_ATTRIBUTE_LOOP_MAXIMUM)),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_LOOP_CONDITION, Set.of(BpmnModelConstants.BPMN_ATTRIBUTE_LANGUAGE)));
    private static final Map<String, Set<QualifiedAttribute>> QUALIFIED_ATTRIBUTES = Map.ofEntries(Map.entry(BpmnModelConstants.BPMN_ELEMENT_DEFINITIONS,
                    Set.of(new QualifiedAttribute(BpmnModelConstants.XSI_NS, "schemaLocation"))),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_CONDITION_EXPRESSION,
                    Set.of(new QualifiedAttribute(BpmnModelConstants.XSI_NS, BpmnModelConstants.XSI_ATTRIBUTE_TYPE))),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_LOOP_CONDITION,
                    Set.of(new QualifiedAttribute(BpmnModelConstants.XSI_NS, BpmnModelConstants.XSI_ATTRIBUTE_TYPE))),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_TIME_DURATION,
                    Set.of(new QualifiedAttribute(BpmnModelConstants.XSI_NS, BpmnModelConstants.XSI_ATTRIBUTE_TYPE))),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_TIME_DATE,
                    Set.of(new QualifiedAttribute(BpmnModelConstants.XSI_NS, BpmnModelConstants.XSI_ATTRIBUTE_TYPE))),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_TIME_CYCLE,
                    Set.of(new QualifiedAttribute(BpmnModelConstants.XSI_NS, BpmnModelConstants.XSI_ATTRIBUTE_TYPE))),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_CALL_ACTIVITY,
                    Set.of(cf(BpmnModelConstants.CF_ATTRIBUTE_CLASSPATH), cf(BpmnModelConstants.CF_ATTRIBUTE_VERSION))),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_SCRIPT_TASK, Set.of(cf(BpmnModelConstants.CF_ATTRIBUTE_EXECUTION))),
            Map.entry(BpmnModelConstants.BPMN_ELEMENT_MULTI_INSTANCE_LOOP_CHARACTERISTICS,
                    Set.of(cf(BpmnModelConstants.BPMN_EXT_ATTRIBUTE_COLLECTION),
                            cf(BpmnModelConstants.BPMN_EXT_ATTRIBUTE_ITEM),
                            cf(BpmnModelConstants.BPMN_EXT_ATTRIBUTE_INDEX),
                            cf(BpmnModelConstants.BPMN_EXT_ATTRIBUTE_ITEM_TYPE),
                            cf(BpmnModelConstants.BPMN_EXT_ATTRIBUTE_TARGET),
                            cf(BpmnModelConstants.BPMN_EXT_ATTRIBUTE_SOURCE))));

    private BpmnAttributeContract() {
    }

    static void validate(XmlSource source) {
        String elementName = source.getLocalName();
        if (!BpmnModelConstants.BPMN20_NS.equals(source.getNamespaceURI())) {
            throw unsupported("Unsupported namespace for BPMN element " + elementName + ": " + source.getNamespaceURI());
        }

        Set<String> unqualified = UNQUALIFIED_ATTRIBUTES.get(elementName);
        if (unqualified == null) {
            throw unsupported("Unsupported BPMN element: " + elementName);
        }
        Set<QualifiedAttribute> qualified = QUALIFIED_ATTRIBUTES.getOrDefault(elementName, Set.of());
        for (int index = 0; index < source.getAttributeCount(); index++) {
            String namespace = source.getAttributeNamespace(index);
            String localName = source.getAttributeLocalName(index);
            boolean supported = namespace == null || namespace.isEmpty()
                    ? unqualified.contains(localName)
                    : qualified.contains(new QualifiedAttribute(namespace, localName));
            if (!supported) {
                String displayName =
                        namespace == null || namespace.isEmpty() ? localName : "{" + namespace + "}" + localName;
                throw unsupported("Unsupported BPMN attribute '" + displayName + "' on " + elementName);
            }
        }
    }

    private static Set<String> idAndName() {
        return Set.of(BpmnModelConstants.BPMN_ATTRIBUTE_ID, BpmnModelConstants.BPMN_ATTRIBUTE_NAME);
    }

    private static Set<String> idNameAndGatewayDirection() {
        return Set.of(BpmnModelConstants.BPMN_ATTRIBUTE_ID, BpmnModelConstants.BPMN_ATTRIBUTE_NAME,
                BpmnModelConstants.BPMN_ATTRIBUTE_GATEWAY_DIRECTION);
    }

    private static Set<String> idNameDefaultAndGatewayDirection() {
        return Set.of(BpmnModelConstants.BPMN_ATTRIBUTE_ID, BpmnModelConstants.BPMN_ATTRIBUTE_NAME,
                BpmnModelConstants.BPMN_ATTRIBUTE_DEFAULT, BpmnModelConstants.BPMN_ATTRIBUTE_GATEWAY_DIRECTION);
    }

    private static QualifiedAttribute cf(String localName) {
        return new QualifiedAttribute(BpmnModelConstants.CF_NS, localName);
    }

    private static CompileFlowException unsupported(String message) {
        return new CompileFlowException(ErrorCode.CF_VALIDATION_002, message, null);
    }

    private record QualifiedAttribute(String namespace, String localName) {}
}
