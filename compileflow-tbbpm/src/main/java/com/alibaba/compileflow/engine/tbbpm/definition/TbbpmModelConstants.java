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
package com.alibaba.compileflow.engine.tbbpm.definition;

/**
 * @author yusu
 */
public class TbbpmModelConstants {

    public static final String COMPILE_FLOW = "cf";

    public static final String BPM = "bpm";

    public static final String START = "start";
    public static final String END = "end";
    public static final String AUTO_TASK = "autoTask";
    public static final String WAIT_TASK = "waitTask";
    public static final String WAIT_EVENT_TASK = "waitEventTask";
    public static final String SCRIPT_TASK = "scriptTask";
    public static final String DECISION = "decision";
    public static final String PARALLEL = "parallel";
    public static final String INCLUSIVE = "inclusive";
    public static final String NOTE = "note";
    public static final String SUB_BPM = "subBpm";
    public static final String LOOP_PROCESS = "loopProcess";
    public static final String CONTINUE = "continue";
    public static final String BREAK = "break";

    public static final String TRANSITION = "transition";

    public static final String VAR = "var";

    public static final String ACTION = "action";
    public static final String IN_ACTION = "inAction";
    public static final String OUT_ACTION = "outAction";

    public static final String ACTION_HANDLE = "actionHandle";
    public static final String PROCESS_ACTION_HANDLE = "processActionHandle";
    public static final String JAVA_ACTION_HANDLE = "javaActionHandle";
    public static final String SPRING_BEAN_ACTION_HANDLE = "spring-beanActionHandle";
    public static final String JAVA_SOURCE_ACTION_HANDLE = "java-sourceActionHandle";
    public static final String JAVA_INLINE_ACTION_HANDLE = "java-inlineActionHandle";
    public static final String MVEL_ACTION_HANDLE = "mvelActionHandle";
    public static final String QL_ACTION_HANDLE = "qlActionHandle";

    public static final String CODE = "code";
    public static final String IMPORTS = "imports";
    public static final String IMPORT = "import";

    public static final String JOB_POLICY = "jobPolicy";
    public static final String JOB_TIMEOUT = "timeout";
    public static final String JOB_RETRY = "retry";
    public static final String JOB_RETRY_ON = "retryOn";
    public static final String JOB_ON_FAILURE = "onFailure";

    // Attributes
    public static final String ATTRIBUTE_ID = "id";
    public static final String ATTRIBUTE_NAME = "name";

    // Common node attributes
    public static final String ATTRIBUTE_DESCRIPTION = "description";
    public static final String ATTRIBUTE_TAG = "tag";
    public static final String ATTRIBUTE_G = "g";
    public static final String ATTRIBUTE_TYPE = "type";
    public static final String ATTRIBUTE_MODE = "mode";
    public static final String ATTRIBUTE_EVENT = "event";

    // Process-level attributes
    public static final String ATTRIBUTE_CODE = "code";

    // Transition attributes
    public static final String ATTRIBUTE_TO = "to";
    public static final String ATTRIBUTE_PRIORITY = "priority";
    public static final String ATTRIBUTE_EXPRESSION = "expression";

    // Sub-process attributes
    public static final String ATTRIBUTE_SUB_BPM_CODE = "subBpmCode";
    public static final String ATTRIBUTE_WAIT_FOR_COMPLETION = "waitForCompletion";
    public static final String ATTRIBUTE_WAIT_FOR_TRIGGER = "waitForTrigger";

    // Var attributes
    public static final String ATTRIBUTE_IN_OUT_TYPE = "inOutType";
    public static final String ATTRIBUTE_DATA_TYPE = "dataType";
    public static final String ATTRIBUTE_DEFAULT_VALUE = "defaultValue";
    public static final String ATTRIBUTE_CONTEXT_VAR_NAME = "contextVarName";

    // Note attribute
    public static final String ATTRIBUTE_COMMENT = "comment";

    // Loop process attributes
    public static final String ATTRIBUTE_LOOP_TYPE = "loopType";
    public static final String ATTRIBUTE_VARIABLE_CLASS = "variableClass";
    public static final String ATTRIBUTE_VARIABLE_NAME = "variableName";
    public static final String ATTRIBUTE_INDEX_VAR_NAME = "indexVarName";
    public static final String ATTRIBUTE_COLLECTION_VAR_NAME = "collectionVarName";
    public static final String ATTRIBUTE_WHILE_EXPRESSION = "whileExpression";
    public static final String ATTRIBUTE_START_NODE_ID = "startNodeId";
    public static final String ATTRIBUTE_END_NODE_ID = "endNodeId";

    // Action handle common attributes
    public static final String ATTRIBUTE_PROCESS = "process";
    public static final String ATTRIBUTE_METHOD = "method";
    public static final String ATTRIBUTE_BEAN = "bean";
    public static final String ATTRIBUTE_CLAZZ = "clazz";
    public static final String ATTRIBUTE_CODE_LITERAL = "code";

}
