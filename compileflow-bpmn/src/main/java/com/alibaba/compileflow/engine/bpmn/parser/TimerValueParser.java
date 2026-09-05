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

import com.alibaba.compileflow.engine.bpmn.model.TimerValue;
import com.alibaba.compileflow.engine.core.xml.parser.ParseContext;
import com.alibaba.compileflow.engine.core.xml.parser.XmlSource;
import com.alibaba.compileflow.engine.core.model.Element;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Parses one supported BPMN timer value without introducing a general event-definition hierarchy.
 *
 * @author yusu
 */
public final class TimerValueParser extends AbstractBpmnElementParser<TimerValue> {
    private final String name;
    private final TimerValue.Kind kind;

    public TimerValueParser(String name, TimerValue.Kind kind) {
        this.name = name;
        this.kind = kind;
    }

    @Override
    protected TimerValue doParse(XmlSource source, ParseContext context) throws Exception {
        BpmnJavaExpressionParser.ExpressionContract expressionContract =
                BpmnJavaExpressionParser.contract(source, context);
        String raw = source.getElementText();
        String value = raw == null ? "" : raw.trim();
        boolean literal = kind == TimerValue.Kind.DURATION && isDuration(value)
                || kind == TimerValue.Kind.DATE && isInstant(value) || kind == TimerValue.Kind.CYCLE;
        return new TimerValue(kind,
                literal ? value : BpmnJavaExpressionParser.validateExpression(expressionContract, raw), !literal);
    }

    @Override
    protected void parseChildElements(XmlSource source, TimerValue value, ParseContext context) {}

    @Override
    protected void attachChildElement(Element child, TimerValue value, ParseContext context) {}

    @Override
    public String getName() {
        return name;
    }

    private static boolean isDuration(String value) {
        try {
            Duration.parse(value);
            return true;
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }

    private static boolean isInstant(String value) {
        try {
            Instant.parse(value);
            return true;
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }
}
