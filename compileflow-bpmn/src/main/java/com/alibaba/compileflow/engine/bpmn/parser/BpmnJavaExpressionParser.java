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
        return new ExpressionContract(source.getLocalName(),
                source.getString(BpmnModelConstants.BPMN_ATTRIBUTE_LANGUAGE), formalExpressionType,
                inheritsJavaLanguage(context));
    }

    static String validateExpression(ExpressionContract contract, String value) {
        validate(contract);
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

    private static void validate(ExpressionContract contract) {
        String elementName = contract.elementName();
        String language = contract.language();
        if (language != null && !"java".equals(language)) {
            throw unsupported("BPMN " + elementName + " language must be 'java': " + language);
        }
        if (language == null && !contract.inheritsJavaLanguage()) {
            throw unsupported(
                    "BPMN " + elementName + " must declare language=\"java\" or definitions expressionLanguage=\""
                    + BpmnModelConstants.COMPILEFLOW_JAVA_EXPRESSION_LANGUAGE + "\"; expressions are compiled as Java");
        }
        if (contract.formalExpressionType() != null && !isFormalExpressionType(contract.formalExpressionType())) {
            throw unsupported(
                    "BPMN " + elementName + " xsi:type must be 'tFormalExpression': " + contract.formalExpressionType());
        }
        if (language != null && contract.formalExpressionType() == null) {
            throw unsupported("BPMN " + elementName + " language requires xsi:type=\"tFormalExpression\"");
        }
    }

    private static boolean inheritsJavaLanguage(ParseContext context) {
        return context.getTop() instanceof Definitions definitions
                && BpmnModelConstants.COMPILEFLOW_JAVA_EXPRESSION_LANGUAGE.equals(definitions.getExpressionLanguage());
    }

    private static boolean isFormalExpressionType(String type) {
        return "tFormalExpression".equals(type) || type != null && type.endsWith(":tFormalExpression");
    }

    private static CompileFlowException unsupported(String message) {
        return new CompileFlowException(ErrorCode.CF_VALIDATION_002, message, null);
    }

    record ExpressionContract(String elementName, String language, String formalExpressionType,
            boolean inheritsJavaLanguage) {}
}
