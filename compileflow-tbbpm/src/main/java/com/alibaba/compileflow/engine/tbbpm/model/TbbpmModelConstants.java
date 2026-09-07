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
package com.alibaba.compileflow.engine.tbbpm.model;

/**
 * Constants for TBBPM model element names and attributes.
 *
 * @author yusu
 */
public final class TbbpmModelConstants {
    private TbbpmModelConstants() {
    }

    public static final String BPM = "bpm";
    public static final String START = "start";
    public static final String END = "end";
    public static final String AUTO_TASK = "autoTask";
    public static final String WAIT_TASK = "waitTask";
    public static final String WAIT_EVENT_TASK = "waitEventTask";
    public static final String TIMER_TASK = "timerTask";
    public static final String SCRIPT_TASK = "scriptTask";
    public static final String EXCLUSIVE = "exclusive";
    public static final String PARALLEL = "parallel";
    public static final String INCLUSIVE = "inclusive";
    public static final String NOTE = "note";
    public static final String SUB_BPM = "subBpm";
    public static final String BPM_CALL = "bpmCall";
    public static final String WHILE = "while";
    public static final String FOREACH = "foreach";
    public static final String INPUT = "input";
    public static final String OUTPUT = "output";
    public static final String CONTINUE = "continue";
    public static final String BREAK = "break";
    public static final String TRANSITION = "transition";
    public static final String VAR = "var";
    public static final String ACTION = "action";
    public static final String EFFECT_POLICY = "effectPolicy";
    public static final String RECONCILE_ACTION = "reconcileAction";
    public static final String CODE = "code";
    public static final String INVOCATION_POLICY_ELEMENT = "invocationPolicy";
    public static final String INVOCATION_POLICY_TIMEOUT = "timeout";
    public static final String INVOCATION_POLICY_ATTEMPT_TIMEOUT = "attemptTimeout";
    public static final String INVOCATION_POLICY_MAX_ATTEMPTS = "maxAttempts";
    public static final String INVOCATION_POLICY_INITIAL_BACKOFF = "initialBackoff";
    public static final String INVOCATION_POLICY_BACKOFF_MULTIPLIER = "backoffMultiplier";
    public static final String INVOCATION_POLICY_MAX_BACKOFF = "maxBackoff";
    public static final String INVOCATION_POLICY_JITTER = "jitter";
    public static final String INVOCATION_POLICY_RETRY_ON = "retryOn";
    public static final String INVOCATION_POLICY_ON_FAILURE = "onFailure";
    public static final String EFFECT_POLICY_RECOVERY = "recovery";
    public static final String EFFECT_POLICY_RECOVERY_PLAN_VARIABLE = "recoveryPlanVariable";
    public static final String EFFECT_POLICY_MAX_ATTEMPTS = "maxAttempts";
    public static final String EFFECT_POLICY_MAX_RECONCILE = "maxReconcileAttempts";
    public static final String EFFECT_POLICY_RECOVERY_DELAY = "recoveryDelay";
    public static final String EFFECT_POLICY_MAX_RECOVERY_DURATION = "maxRecoveryDuration";
    // Attributes
    public static final String ATTRIBUTE_ID = "id";
    public static final String ATTRIBUTE_NAME = "name";
    // Common node attributes
    public static final String ATTRIBUTE_DESCRIPTION = "description";
    public static final String ATTRIBUTE_G = "g";
    public static final String ATTRIBUTE_TYPE = "type";
    public static final String ATTRIBUTE_EVENT = "event";
    public static final String ATTRIBUTE_TIMEOUT = "timeout";
    public static final String ATTRIBUTE_DURATION = "duration";
    public static final String ATTRIBUTE_DURATION_EXPRESSION = "durationExpression";
    public static final String ATTRIBUTE_WAKE_AT_EXPRESSION = "wakeAtExpression";
    // Process-level attributes
    public static final String ATTRIBUTE_CODE = "code";
    // Transition attributes
    public static final String ATTRIBUTE_TO = "to";
    public static final String ATTRIBUTE_CONDITION = "condition";
    public static final String ATTRIBUTE_EXECUTION = "execution";
    public static final String ATTRIBUTE_LANGUAGE = "language";
    // BPM call target attributes
    public static final String ATTRIBUTE_CLASSPATH = "classpath";
    public static final String ATTRIBUTE_VERSION = "version";
    // Variable attributes
    public static final String ATTRIBUTE_IN_OUT_TYPE = "inOutType";
    public static final String ATTRIBUTE_DATA_TYPE = "dataType";
    public static final String ATTRIBUTE_DEFAULT_VALUE = "defaultValue";
    public static final String ATTRIBUTE_SOURCE = "source";
    public static final String ATTRIBUTE_TARGET = "target";
    // Note attribute
    public static final String ATTRIBUTE_COMMENT = "comment";
    // Loop attributes
    public static final String ATTRIBUTE_INDEX = "index";
    public static final String ATTRIBUTE_ITEM_TYPE = "itemType";
    public static final String ATTRIBUTE_ITEM = "item";
    public static final String ATTRIBUTE_COLLECTION = "collection";
    public static final String ATTRIBUTE_MAX_ITERATIONS = "maxIterations";
    // Action attributes
    public static final String ATTRIBUTE_METHOD = "method";
    public static final String ATTRIBUTE_BEAN = "bean";
    public static final String ATTRIBUTE_CLASS = "class";
}
