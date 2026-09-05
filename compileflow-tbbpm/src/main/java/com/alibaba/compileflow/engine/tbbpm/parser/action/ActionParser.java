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

import com.alibaba.compileflow.engine.core.xml.parser.ParseContext;
import com.alibaba.compileflow.engine.core.xml.parser.XmlSource;
import com.alibaba.compileflow.engine.core.model.Element;
import com.alibaba.compileflow.engine.core.model.action.Action;
import com.alibaba.compileflow.engine.core.model.action.ActionExecution;
import com.alibaba.compileflow.engine.core.model.action.ActionType;
import com.alibaba.compileflow.engine.core.model.action.EffectPolicy;
import com.alibaba.compileflow.engine.core.model.action.InvocationPolicy;
import com.alibaba.compileflow.engine.core.model.action.ScriptSource;
import com.alibaba.compileflow.engine.core.model.mapping.InputMapping;
import com.alibaba.compileflow.engine.core.model.mapping.OutputMapping;
import com.alibaba.compileflow.engine.tbbpm.parser.AbstractTbbpmElementParser;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModelConstants;

/**
 * XML parser for TBBPM action elements.
 *
 * @author wuxiang
 * @author yusu
 */
public class ActionParser extends AbstractTbbpmElementParser<Action> {
    @Override
    protected Action doParse(XmlSource xmlSource, ParseContext parseContext) {
        Action action = new Action();
        ActionType type = ActionType.of(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_TYPE));
        action.setType(type);
        action.setExecution(ActionExecution.of(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_EXECUTION)));
        ActionParsing.parseDefinition(xmlSource, action);
        return action;
    }

    @Override
    protected void attachChildElement(Element childElement, Action element, ParseContext parseContext) {
        if (childElement instanceof InputMapping input) {
            element.addInputMapping(input);
        } else if (childElement instanceof OutputMapping output) {
            element.addOutputMapping(output);
        } else if (childElement instanceof ScriptSource source && element.getType() == ActionType.SCRIPT) {
            if (element.getSource() != null) {
                throw ActionParsing.duplicateChild(TbbpmModelConstants.CODE, element.getType());
            }
            element.setSource(source.getSource());
        } else if (childElement instanceof EffectPolicy effectPolicy) {
            if (element.getEffectPolicy() != null) {
                throw new IllegalArgumentException("An action must declare at most one effectPolicy element");
            }
            element.setEffectPolicy(effectPolicy);
        } else if (childElement instanceof InvocationPolicy invocationPolicy) {
            if (element.getInvocationPolicy() != null) {
                throw new IllegalArgumentException("An action must declare at most one invocationPolicy element");
            }
            element.setInvocationPolicy(invocationPolicy);
        } else {
            super.attachChildElement(childElement, element, parseContext);
        }
    }

    @Override
    public String getName() {
        return TbbpmModelConstants.ACTION;
    }
}
