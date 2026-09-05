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
import com.alibaba.compileflow.engine.bpmn.model.Expression;
import com.alibaba.compileflow.engine.core.xml.parser.ParseContext;
import com.alibaba.compileflow.engine.core.xml.parser.XmlSource;
import com.alibaba.compileflow.engine.core.model.Element;

/**
 * XML parser for BPMN condition expressions.
 *
 * @author yusu
 */
public class ConditionExpressionParser extends AbstractBpmnElementParser<Expression> {
    @Override
    protected Expression doParse(XmlSource xmlSource, ParseContext parseContext) throws Exception {
        Expression expression = new Expression();
        expression.setValue(BpmnJavaExpressionParser.parse(xmlSource, parseContext));
        return expression;
    }

    @Override
    protected void parseChildElements(XmlSource xmlSource, Expression element, ParseContext parseContext)
            throws Exception {}

    @Override
    protected void attachChildElement(Element childElement, Expression element, ParseContext parseContext) {}

    @Override
    public String getName() {
        return BpmnModelConstants.BPMN_ELEMENT_CONDITION_EXPRESSION;
    }
}
