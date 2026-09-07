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
import com.alibaba.compileflow.engine.bpmn.model.Definitions;
import com.alibaba.compileflow.engine.core.xml.parser.ParseContext;
import com.alibaba.compileflow.engine.core.xml.parser.XmlSource;

/**
 * Enforces and parses BPMN formal expressions compiled as Java.
 *
 * @author yusu
 */
final class BpmnJavaExpressionParser {
    private BpmnJavaExpressionParser() {
    }

    static String parse(XmlSource source, ParseContext context) throws Exception {
        ExpressionContract contract = contract(source, context);
        return validateExpression(contract, source.getElementText());
    }

    static ExpressionContract contract(XmlSource source, ParseContext context) {
        String formalExpressionType = null;
        for (int index = 0; index < source.getAttributeCount(); index++) {
            if (BpmnModelConstants.XSI_NS.equals(source.getAttributeNamespace(index))
                    && BpmnModelConstants.XSI_ATTRIBUTE_TYPE.equals(source.getAttributeLocalName(index))) {
                formalExpressionType = source.getAttributeValue(index);
                break;
            }
        }
        String elementName = source.getLocalName();
        String language = source.getString(BpmnModelConstants.BPMN_ATTRIBUTE_LANGUAGE);
        if (language != null && !"java".equals(language)) {
            throw unsupported("BPMN " + elementName + " language must be 'java': " + language);
        }
        if (formalExpressionType != null && !isFormalExpressionType(source, formalExpressionType)) {
            throw unsupported(
                    "BPMN " + elementName + " xsi:type must resolve to BPMN tFormalExpression: " + formalExpressionType);
        }
        if (language != null && formalExpressionType == null) {
            throw unsupported("BPMN " + elementName + " language requires xsi:type=\"tFormalExpression\"");
        }
        return new ExpressionContract(elementName, language, inheritsJavaLanguage(context));
    }

    static String validateExpression(ExpressionContract contract, String value) {
        if (contract.language() == null && !contract.inheritsJavaLanguage()) {
            throw unsupported(
                    "BPMN " + contract.elementName()
                    + " must declare language=\"java\" or definitions expressionLanguage=\""
                    + BpmnModelConstants.COMPILEFLOW_JAVA_EXPRESSION_LANGUAGE + "\"; expressions are compiled as Java");
        }
        if (value == null) {
            return null;
        }
        String expression = value.trim();
        if (expression.startsWith("${") || expression.startsWith("#{")) {
            throw unsupported(
                    "BPMN Java formal expressions must contain the raw expression body; wrappers are not supported");
        }
        return expression;
    }

    private static boolean inheritsJavaLanguage(ParseContext context) {
        return context.getTop() instanceof Definitions definitions
                && BpmnModelConstants.COMPILEFLOW_JAVA_EXPRESSION_LANGUAGE.equals(definitions.getExpressionLanguage());
    }

    private static boolean isFormalExpressionType(XmlSource source, String value) {
        String type = value.trim();
        int separator = type.indexOf(':');
        String prefix = separator < 0 ? "" : type.substring(0, separator);
        String localName = type.substring(separator + 1);
        return separator != 0 && "tFormalExpression".equals(localName)
                && BpmnModelConstants.BPMN20_NS.equals(source.getNamespaceURI(prefix));
    }

    private static CompileFlowException unsupported(String message) {
        return new CompileFlowException(ErrorCode.CF_VALIDATION_002, message, null);
    }

    record ExpressionContract(String elementName, String language, boolean inheritsJavaLanguage) {}
}
