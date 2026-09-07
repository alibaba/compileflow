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
package com.alibaba.compileflow.engine.tbbpm.parser.action;

import com.alibaba.compileflow.engine.core.model.action.Action;
import com.alibaba.compileflow.engine.core.model.action.ActionType;
import com.alibaba.compileflow.engine.core.model.action.ReconcileAction;
import com.alibaba.compileflow.engine.core.xml.parser.XmlSource;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModelConstants;
import java.util.Arrays;
import java.util.Set;

/**
 * Validation and population helpers for flattened TBBPM actions.
 *
 * @author yusu
 */
final class ActionParsing {
    private ActionParsing() {
    }

    static void requireOnlyAttributes(XmlSource source, String... allowedAttributes) {
        Set<String> allowed = Set.of(allowedAttributes);
        for (int index = 0; index < source.getAttributeCount(); index++) {
            String attribute = source.getAttributeLocalName(index);
            if (!allowed.contains(attribute)) {
                throw new IllegalArgumentException(
                        "Action attribute '" + attribute + "' is not valid for " + source.getLocalName());
            }
        }
    }

    static IllegalArgumentException duplicateChild(String childName, ActionType actionType) {
        return new IllegalArgumentException(
                "Action type " + actionType.getValue() + " must declare at most one " + childName + " element");
    }

    static void parseDefinition(XmlSource source, Action action) {
        String[] common = {TbbpmModelConstants.ATTRIBUTE_TYPE, TbbpmModelConstants.ATTRIBUTE_EXECUTION};
        switch (action.getType()) {
            case JAVA -> {
                requireOnlyAttributes(source,
                        append(common, TbbpmModelConstants.ATTRIBUTE_CLASS, TbbpmModelConstants.ATTRIBUTE_METHOD));
                action.setClassName(source.getString(TbbpmModelConstants.ATTRIBUTE_CLASS));
                action.setMethod(source.getString(TbbpmModelConstants.ATTRIBUTE_METHOD));
            }
            case SPRING_BEAN -> {
                requireOnlyAttributes(source,
                        append(common, TbbpmModelConstants.ATTRIBUTE_BEAN, TbbpmModelConstants.ATTRIBUTE_CLASS,
                                TbbpmModelConstants.ATTRIBUTE_METHOD));
                action.setBean(source.getString(TbbpmModelConstants.ATTRIBUTE_BEAN));
                action.setClassName(source.getString(TbbpmModelConstants.ATTRIBUTE_CLASS));
                action.setMethod(source.getString(TbbpmModelConstants.ATTRIBUTE_METHOD));
            }
            case SCRIPT -> {
                requireOnlyAttributes(source, append(common, TbbpmModelConstants.ATTRIBUTE_LANGUAGE));
                action.setLanguage(source.getString(TbbpmModelConstants.ATTRIBUTE_LANGUAGE));
            }
        }
    }

    static void parseDefinition(XmlSource source, ReconcileAction action) {
        String[] common = {TbbpmModelConstants.ATTRIBUTE_TYPE};
        switch (action.getType()) {
            case JAVA -> {
                requireOnlyAttributes(source,
                        append(common, TbbpmModelConstants.ATTRIBUTE_CLASS, TbbpmModelConstants.ATTRIBUTE_METHOD));
                action.setClassName(source.getString(TbbpmModelConstants.ATTRIBUTE_CLASS));
                action.setMethod(source.getString(TbbpmModelConstants.ATTRIBUTE_METHOD));
            }
            case SPRING_BEAN -> {
                requireOnlyAttributes(source,
                        append(common, TbbpmModelConstants.ATTRIBUTE_BEAN, TbbpmModelConstants.ATTRIBUTE_CLASS,
                                TbbpmModelConstants.ATTRIBUTE_METHOD));
                action.setBean(source.getString(TbbpmModelConstants.ATTRIBUTE_BEAN));
                action.setClassName(source.getString(TbbpmModelConstants.ATTRIBUTE_CLASS));
                action.setMethod(source.getString(TbbpmModelConstants.ATTRIBUTE_METHOD));
            }
            case SCRIPT -> {
                requireOnlyAttributes(source, append(common, TbbpmModelConstants.ATTRIBUTE_LANGUAGE));
                action.setLanguage(source.getString(TbbpmModelConstants.ATTRIBUTE_LANGUAGE));
            }
        }
    }

    private static String[] append(String[] values, String... additions) {
        String[] result = Arrays.copyOf(values, values.length + additions.length);
        System.arraycopy(additions, 0, result, values.length, additions.length);
        return result;
    }
}
