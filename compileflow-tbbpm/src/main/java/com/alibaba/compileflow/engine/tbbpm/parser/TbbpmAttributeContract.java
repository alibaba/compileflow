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
package com.alibaba.compileflow.engine.tbbpm.parser;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.core.xml.parser.XmlSource;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModelConstants;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Defines the TBBPM attributes that CompileFlow preserves and executes.
 *
 * <p>This contract remains active when callers explicitly disable XSD
 * validation, so misspelled execution properties are never ignored.</p>
 *
 * @author yusu
 */
final class TbbpmAttributeContract {
    private static final Set<String> COMMON_NODE_ATTRIBUTES = Set.of(TbbpmModelConstants.ATTRIBUTE_ID,
            TbbpmModelConstants.ATTRIBUTE_NAME, TbbpmModelConstants.ATTRIBUTE_DESCRIPTION,
            TbbpmModelConstants.ATTRIBUTE_G);
    private static final Map<String, Set<String>> ATTRIBUTES = Map.ofEntries(Map.entry(TbbpmModelConstants.BPM,
                    Set.of(TbbpmModelConstants.ATTRIBUTE_CODE, TbbpmModelConstants.ATTRIBUTE_NAME,
                            TbbpmModelConstants.ATTRIBUTE_DESCRIPTION)),
            Map.entry(TbbpmModelConstants.START, COMMON_NODE_ATTRIBUTES),
            Map.entry(TbbpmModelConstants.END, COMMON_NODE_ATTRIBUTES),
            Map.entry(TbbpmModelConstants.AUTO_TASK, COMMON_NODE_ATTRIBUTES),
            Map.entry(TbbpmModelConstants.SCRIPT_TASK, COMMON_NODE_ATTRIBUTES),
            Map.entry(TbbpmModelConstants.EXCLUSIVE, COMMON_NODE_ATTRIBUTES),
            Map.entry(TbbpmModelConstants.PARALLEL, COMMON_NODE_ATTRIBUTES),
            Map.entry(TbbpmModelConstants.INCLUSIVE, COMMON_NODE_ATTRIBUTES),
            Map.entry(TbbpmModelConstants.WAIT_TASK,
                    union(COMMON_NODE_ATTRIBUTES, TbbpmModelConstants.ATTRIBUTE_TIMEOUT)),
            Map.entry(TbbpmModelConstants.TIMER_TASK,
                    union(COMMON_NODE_ATTRIBUTES, TbbpmModelConstants.ATTRIBUTE_DURATION,
                            TbbpmModelConstants.ATTRIBUTE_DURATION_EXPRESSION,
                            TbbpmModelConstants.ATTRIBUTE_WAKE_AT_EXPRESSION)),
            Map.entry(TbbpmModelConstants.WAIT_EVENT_TASK,
                    union(COMMON_NODE_ATTRIBUTES, TbbpmModelConstants.ATTRIBUTE_EVENT,
                            TbbpmModelConstants.ATTRIBUTE_TIMEOUT)),
            Map.entry(TbbpmModelConstants.WHILE,
                    union(COMMON_NODE_ATTRIBUTES, TbbpmModelConstants.ATTRIBUTE_CONDITION,
                            TbbpmModelConstants.ATTRIBUTE_MAX_ITERATIONS, TbbpmModelConstants.ATTRIBUTE_INDEX)),
            Map.entry(TbbpmModelConstants.FOREACH,
                    union(COMMON_NODE_ATTRIBUTES, TbbpmModelConstants.ATTRIBUTE_COLLECTION,
                            TbbpmModelConstants.ATTRIBUTE_ITEM, TbbpmModelConstants.ATTRIBUTE_ITEM_TYPE,
                            TbbpmModelConstants.ATTRIBUTE_INDEX, TbbpmModelConstants.ATTRIBUTE_EXECUTION)),
            Map.entry(TbbpmModelConstants.OUTPUT,
                    Set.of(TbbpmModelConstants.ATTRIBUTE_DATA_TYPE, TbbpmModelConstants.ATTRIBUTE_TARGET,
                            TbbpmModelConstants.ATTRIBUTE_SOURCE)),
            Map.entry(TbbpmModelConstants.INPUT,
                    Set.of(TbbpmModelConstants.ATTRIBUTE_DATA_TYPE, TbbpmModelConstants.ATTRIBUTE_SOURCE,
                            TbbpmModelConstants.ATTRIBUTE_TARGET, TbbpmModelConstants.ATTRIBUTE_DEFAULT_VALUE)),
            Map.entry(TbbpmModelConstants.CONTINUE,
                    union(COMMON_NODE_ATTRIBUTES, TbbpmModelConstants.ATTRIBUTE_CONDITION)),
            Map.entry(TbbpmModelConstants.BREAK, union(COMMON_NODE_ATTRIBUTES, TbbpmModelConstants.ATTRIBUTE_CONDITION)),
            Map.entry(TbbpmModelConstants.SUB_BPM, COMMON_NODE_ATTRIBUTES),
            Map.entry(TbbpmModelConstants.BPM_CALL,
                    union(COMMON_NODE_ATTRIBUTES, TbbpmModelConstants.ATTRIBUTE_CODE,
                            TbbpmModelConstants.ATTRIBUTE_CLASSPATH, TbbpmModelConstants.ATTRIBUTE_VERSION)),
            Map.entry(TbbpmModelConstants.NOTE, union(COMMON_NODE_ATTRIBUTES, TbbpmModelConstants.ATTRIBUTE_COMMENT)),
            Map.entry(TbbpmModelConstants.TRANSITION,
                    Set.of(TbbpmModelConstants.ATTRIBUTE_NAME, TbbpmModelConstants.ATTRIBUTE_TO,
                            TbbpmModelConstants.ATTRIBUTE_G, TbbpmModelConstants.ATTRIBUTE_CONDITION)),
            Map.entry(TbbpmModelConstants.VAR,
                    Set.of(TbbpmModelConstants.ATTRIBUTE_NAME, TbbpmModelConstants.ATTRIBUTE_IN_OUT_TYPE,
                            TbbpmModelConstants.ATTRIBUTE_DESCRIPTION, TbbpmModelConstants.ATTRIBUTE_DEFAULT_VALUE,
                            TbbpmModelConstants.ATTRIBUTE_DATA_TYPE)),
            Map.entry(TbbpmModelConstants.INVOCATION_POLICY_ELEMENT,
                    Set.of(TbbpmModelConstants.INVOCATION_POLICY_TIMEOUT,
                            TbbpmModelConstants.INVOCATION_POLICY_ATTEMPT_TIMEOUT,
                            TbbpmModelConstants.INVOCATION_POLICY_MAX_ATTEMPTS,
                            TbbpmModelConstants.INVOCATION_POLICY_INITIAL_BACKOFF,
                            TbbpmModelConstants.INVOCATION_POLICY_BACKOFF_MULTIPLIER,
                            TbbpmModelConstants.INVOCATION_POLICY_MAX_BACKOFF,
                            TbbpmModelConstants.INVOCATION_POLICY_JITTER, TbbpmModelConstants.INVOCATION_POLICY_RETRY_ON,
                            TbbpmModelConstants.INVOCATION_POLICY_ON_FAILURE)),
            Map.entry(TbbpmModelConstants.ACTION,
                    Set.of(TbbpmModelConstants.ATTRIBUTE_TYPE, TbbpmModelConstants.ATTRIBUTE_EXECUTION,
                            TbbpmModelConstants.ATTRIBUTE_METHOD, TbbpmModelConstants.ATTRIBUTE_CLASS,
                            TbbpmModelConstants.ATTRIBUTE_BEAN, TbbpmModelConstants.ATTRIBUTE_LANGUAGE)),
            Map.entry(TbbpmModelConstants.RECONCILE_ACTION,
                    Set.of(TbbpmModelConstants.ATTRIBUTE_TYPE, TbbpmModelConstants.ATTRIBUTE_METHOD,
                            TbbpmModelConstants.ATTRIBUTE_CLASS, TbbpmModelConstants.ATTRIBUTE_BEAN,
                            TbbpmModelConstants.ATTRIBUTE_LANGUAGE)),
            Map.entry(TbbpmModelConstants.EFFECT_POLICY,
                    Set.of(TbbpmModelConstants.EFFECT_POLICY_RECOVERY_PLAN_VARIABLE,
                            TbbpmModelConstants.EFFECT_POLICY_RECOVERY, TbbpmModelConstants.EFFECT_POLICY_MAX_ATTEMPTS,
                            TbbpmModelConstants.EFFECT_POLICY_MAX_RECONCILE,
                            TbbpmModelConstants.EFFECT_POLICY_RECOVERY_DELAY,
                            TbbpmModelConstants.EFFECT_POLICY_MAX_RECOVERY_DURATION)),
            Map.entry(TbbpmModelConstants.CODE, Set.of()));

    private TbbpmAttributeContract() {
    }

    static void validate(XmlSource source) {
        String elementName = source.getLocalName();
        String namespace = source.getNamespaceURI();
        if (namespace != null && !namespace.isEmpty()) {
            throw unsupported("Unsupported namespace for TBBPM element " + elementName + ": " + namespace);
        }

        Set<String> supported = ATTRIBUTES.get(elementName);
        if (supported == null) {
            throw unsupported("Unsupported TBBPM element: " + elementName);
        }
        for (int index = 0; index < source.getAttributeCount(); index++) {
            String attributeNamespace = source.getAttributeNamespace(index);
            String localName = source.getAttributeLocalName(index);
            if ((attributeNamespace != null && !attributeNamespace.isEmpty()) || !supported.contains(localName)) {
                String displayName = attributeNamespace == null || attributeNamespace.isEmpty()
                        ? localName
                        : "{" + attributeNamespace + "}" + localName;
                throw unsupported("Unsupported TBBPM attribute '" + displayName + "' on " + elementName);
            }
        }
    }

    private static Set<String> union(Set<String> base, String... additionalAttributes) {
        LinkedHashSet<String> attributes = new LinkedHashSet<>(base);
        Collections.addAll(attributes, additionalAttributes);
        return Set.copyOf(attributes);
    }

    private static CompileFlowException unsupported(String message) {
        return new CompileFlowException(ErrorCode.CF_VALIDATION_002, message, null);
    }
}
