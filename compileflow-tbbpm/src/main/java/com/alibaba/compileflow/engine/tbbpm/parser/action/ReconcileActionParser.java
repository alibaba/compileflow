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

import com.alibaba.compileflow.engine.core.model.Element;
import com.alibaba.compileflow.engine.core.model.action.ActionType;
import com.alibaba.compileflow.engine.core.model.action.ReconcileAction;
import com.alibaba.compileflow.engine.core.model.action.ReconcileInput;
import com.alibaba.compileflow.engine.core.model.action.ScriptSource;
import com.alibaba.compileflow.engine.core.model.mapping.InputMapping;
import com.alibaba.compileflow.engine.core.xml.parser.ParseContext;
import com.alibaba.compileflow.engine.core.xml.parser.XmlSource;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModelConstants;
import com.alibaba.compileflow.engine.tbbpm.parser.AbstractTbbpmElementParser;

/**
 * Parser for the recovery-only Action nested under an effect policy.
 *
 * @author yusu
 */
public final class ReconcileActionParser extends AbstractTbbpmElementParser<ReconcileAction> {
    @Override
    protected ReconcileAction doParse(XmlSource source, ParseContext context) {
        ReconcileAction action = new ReconcileAction();
        action.setType(ActionType.of(source.getString(TbbpmModelConstants.ATTRIBUTE_TYPE)));
        ActionParsing.parseDefinition(source, action);
        return action;
    }

    @Override
    protected void attachChildElement(Element child, ReconcileAction action, ParseContext context) {
        if (child instanceof InputMapping input) {
            if (input.getDefaultValue() != null) {
                throw new IllegalArgumentException("reconcile input must not declare defaultValue");
            }
            ReconcileInput reconcileInput = new ReconcileInput();
            reconcileInput.setSource(input.getSource());
            reconcileInput.setTarget(input.getTarget());
            reconcileInput.setDataType(input.getDataType());
            action.addInput(reconcileInput);
        } else if (child instanceof ScriptSource source && action.getType() == ActionType.SCRIPT) {
            if (action.getSource() != null) {
                throw ActionParsing.duplicateChild(TbbpmModelConstants.CODE, action.getType());
            }
            action.setSource(source.getSource());
        } else {
            super.attachChildElement(child, action, context);
        }
    }

    @Override
    public String getName() {
        return TbbpmModelConstants.RECONCILE_ACTION;
    }
}
