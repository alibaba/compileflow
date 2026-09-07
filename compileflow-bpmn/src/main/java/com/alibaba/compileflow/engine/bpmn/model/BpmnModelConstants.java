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
package com.alibaba.compileflow.engine.bpmn.model;

/**
 * BPMN 2.0 element and attribute name constants used by parsers and writers.
 *
 * @author yusu
 */
public final class BpmnModelConstants {
    // ========== Namespaces ==========
    public static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";
    public static final String BPMN20_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    public static final String BPMNDI_NS = "http://www.omg.org/spec/BPMN/20100524/DI";
    public static final String DC_NS = "http://www.omg.org/spec/DD/20100524/DC";
    public static final String DI_NS = "http://www.omg.org/spec/DD/20100524/DI";
    public static final String COMPILEFLOW_JAVA_EXPRESSION_LANGUAGE = "urn:compileflow:java";
    // ========== BPMN 2.0 Element Names ==========
    public static final String BPMN_ELEMENT_DEFINITIONS = "definitions";
    public static final String BPMN_ELEMENT_DOCUMENTATION = "documentation";
    public static final String BPMN_ELEMENT_EXTENSION_ELEMENTS = "extensionElements";
    public static final String BPMN_ELEMENT_CONDITION_EXPRESSION = "conditionExpression";
    public static final String BPMN_ELEMENT_SEQUENCE_FLOW = "sequenceFlow";
    public static final String BPMN_ELEMENT_INCOMING = "incoming";
    public static final String BPMN_ELEMENT_OUTGOING = "outgoing";
    public static final String BPMN_ELEMENT_END_EVENT = "endEvent";
    public static final String BPMN_ELEMENT_MESSAGE = "message";
    public static final String BPMN_ELEMENT_START_EVENT = "startEvent";
    public static final String BPMN_ELEMENT_MESSAGE_EVENT_DEFINITION = "messageEventDefinition";
    public static final String BPMN_ELEMENT_TIMER_EVENT_DEFINITION = "timerEventDefinition";
    public static final String BPMN_ELEMENT_CALL_ACTIVITY = "callActivity";
    public static final String BPMN_ELEMENT_PROCESS = "process";
    public static final String BPMN_ELEMENT_SERVICE_TASK = "serviceTask";
    public static final String BPMN_ELEMENT_SCRIPT_TASK = "scriptTask";
    public static final String BPMN_ELEMENT_RECEIVE_TASK = "receiveTask";
    public static final String BPMN_ELEMENT_SCRIPT = "script";
    public static final String BPMN_ELEMENT_SUB_PROCESS = "subProcess";
    public static final String BPMN_ELEMENT_PARALLEL_GATEWAY = "parallelGateway";
    public static final String BPMN_ELEMENT_EXCLUSIVE_GATEWAY = "exclusiveGateway";
    public static final String BPMN_ELEMENT_INTERMEDIATE_CATCH_EVENT = "intermediateCatchEvent";
    public static final String BPMN_ELEMENT_TIME_DATE = "timeDate";
    public static final String BPMN_ELEMENT_TIME_DURATION = "timeDuration";
    public static final String BPMN_ELEMENT_TIME_CYCLE = "timeCycle";
    public static final String BPMN_ELEMENT_INCLUSIVE_GATEWAY = "inclusiveGateway";
    public static final String BPMN_ELEMENT_STANDARD_LOOP_CHARACTERISTICS = "standardLoopCharacteristics";
    public static final String BPMN_ELEMENT_MULTI_INSTANCE_LOOP_CHARACTERISTICS = "multiInstanceLoopCharacteristics";
    public static final String BPMN_ELEMENT_LOOP_CONDITION = "loopCondition";
    // ========== BPMN Diagram Interchange (BPMNDI) Element Names ==========
    public static final String BPMNDI_ELEMENT_BPMN_DIAGRAM = "BPMNDiagram";
    // ========== XML Schema Instance (XSI) Attribute Names ==========
    public static final String XSI_ATTRIBUTE_TYPE = "type";
    // ========== BPMN 2.0 Attribute Names ==========
    public static final String BPMN_ATTRIBUTE_EXPORTER = "exporter";
    public static final String BPMN_ATTRIBUTE_EXPORTER_VERSION = "exporterVersion";
    public static final String BPMN_ATTRIBUTE_EXPRESSION_LANGUAGE = "expressionLanguage";
    public static final String BPMN_ATTRIBUTE_ID = "id";
    public static final String BPMN_ATTRIBUTE_NAME = "name";
    public static final String BPMN_ATTRIBUTE_TARGET_NAMESPACE = "targetNamespace";
    public static final String BPMN_ATTRIBUTE_TYPE_LANGUAGE = "typeLanguage";
    public static final String BPMN_ATTRIBUTE_IS_EXECUTABLE = "isExecutable";
    public static final String BPMN_ATTRIBUTE_MESSAGE_REF = "messageRef";
    public static final String BPMN_ATTRIBUTE_SOURCE_REF = "sourceRef";
    public static final String BPMN_ATTRIBUTE_TARGET_REF = "targetRef";
    public static final String BPMN_ATTRIBUTE_IS_IMMEDIATE = "isImmediate";
    public static final String BPMN_ATTRIBUTE_LANGUAGE = "language";
    public static final String BPMN_ATTRIBUTE_IS_INTERRUPTING = "isInterrupting";
    public static final String BPMN_ATTRIBUTE_DEFAULT = "default";
    public static final String BPMN_ATTRIBUTE_OPERATION_REF = "operationRef";
    public static final String BPMN_ATTRIBUTE_IMPLEMENTATION = "implementation";
    public static final String BPMN_ATTRIBUTE_SCRIPT_FORMAT = "scriptFormat";
    public static final String BPMN_ATTRIBUTE_TRIGGERED_BY_EVENT = "triggeredByEvent";
    public static final String BPMN_ATTRIBUTE_GATEWAY_DIRECTION = "gatewayDirection";
    public static final String BPMN_ATTRIBUTE_CALLED_ELEMENT = "calledElement";
    public static final String BPMN_ATTRIBUTE_TEST_BEFORE = "testBefore";
    public static final String BPMN_ATTRIBUTE_LOOP_MAXIMUM = "loopMaximum";
    public static final String BPMN_ATTRIBUTE_IS_SEQUENTIAL = "isSequential";
    // ========== CompileFlow Namespace ==========
    public static final String CF_NS = "http://www.compileflow.org";
    public static final String CF_PREFIX = "cf";
    public static final String CF_ATTRIBUTE_CLASSPATH = "classpath";
    public static final String CF_ATTRIBUTE_VERSION = "version";
    public static final String CF_ATTRIBUTE_EXECUTION = "execution";
    // ========== CompileFlow BPMN Extension Attribute Names ==========
    public static final String BPMN_EXT_ATTRIBUTE_CODE = "code";
    public static final String BPMN_EXT_ATTRIBUTE_VAR = "var";
    public static final String BPMN_EXT_ELEMENT_INPUT = "input";
    public static final String BPMN_EXT_ELEMENT_OUTPUT = "output";
    public static final String BPMN_EXT_ATTRIBUTE_COLLECTION = "collection";
    public static final String BPMN_EXT_ATTRIBUTE_ITEM = "item";
    public static final String BPMN_EXT_ATTRIBUTE_ITEM_TYPE = "itemType";
    public static final String BPMN_EXT_ATTRIBUTE_INDEX = "index";
    public static final String BPMN_EXT_ATTRIBUTE_TARGET = "target";
    public static final String BPMN_EXT_ATTRIBUTE_SOURCE = "source";
    public static final String BPMN_EXT_ELEMENT_INVOCATION_POLICY = "invocationPolicy";
    public static final String BPMN_EXT_ATTRIBUTE_INVOCATION_POLICY_TIMEOUT = "timeout";
    public static final String BPMN_EXT_ATTRIBUTE_INVOCATION_POLICY_ATTEMPT_TIMEOUT = "attemptTimeout";
    public static final String BPMN_EXT_ATTRIBUTE_INVOCATION_POLICY_MAX_ATTEMPTS = "maxAttempts";
    public static final String BPMN_EXT_ATTRIBUTE_INVOCATION_POLICY_INITIAL_BACKOFF = "initialBackoff";
    public static final String BPMN_EXT_ATTRIBUTE_INVOCATION_POLICY_BACKOFF_MULTIPLIER = "backoffMultiplier";
    public static final String BPMN_EXT_ATTRIBUTE_INVOCATION_POLICY_MAX_BACKOFF = "maxBackoff";
    public static final String BPMN_EXT_ATTRIBUTE_INVOCATION_POLICY_JITTER = "jitter";
    public static final String BPMN_EXT_ATTRIBUTE_INVOCATION_POLICY_RETRY_ON = "retryOn";
    public static final String BPMN_EXT_ATTRIBUTE_INVOCATION_POLICY_ON_FAILURE = "onFailure";
    public static final String BPMN_EXT_ATTRIBUTE_ACTION = "action";
    public static final String BPMN_EXT_ELEMENT_EFFECT_POLICY = "effectPolicy";
    public static final String BPMN_EXT_ELEMENT_RECONCILE_ACTION = "reconcileAction";

    private BpmnModelConstants() {
    }
}
