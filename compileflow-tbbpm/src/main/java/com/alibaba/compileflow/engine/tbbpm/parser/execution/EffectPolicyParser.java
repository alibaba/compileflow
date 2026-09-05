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
package com.alibaba.compileflow.engine.tbbpm.parser.execution;

import com.alibaba.compileflow.engine.core.xml.parser.ParseContext;
import com.alibaba.compileflow.engine.core.xml.parser.XmlSource;
import com.alibaba.compileflow.engine.core.model.Element;
import com.alibaba.compileflow.engine.core.model.action.EffectPolicy;
import com.alibaba.compileflow.engine.core.model.action.EffectRecovery;
import com.alibaba.compileflow.engine.core.model.action.EffectiveEffectPolicy;
import com.alibaba.compileflow.engine.core.model.action.ReconcileAction;
import com.alibaba.compileflow.engine.tbbpm.parser.AbstractTbbpmElementParser;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModelConstants;
import org.apache.commons.lang3.StringUtils;

/**
 * Parser for the optional parameters subordinate to execution="effect".
 *
 * @author yusu
 */
public final class EffectPolicyParser extends AbstractTbbpmElementParser<EffectPolicy> {
    @Override
    protected EffectPolicy doParse(XmlSource source, ParseContext context) {
        EffectPolicy policy = new EffectPolicy();
        policy.setRecoveryPlanVariable(source.getString(TbbpmModelConstants.EFFECT_POLICY_RECOVERY_PLAN_VARIABLE));
        String recovery = source.getString(TbbpmModelConstants.EFFECT_POLICY_RECOVERY);
        if (StringUtils.isNotBlank(recovery)) {
            policy.setRecovery(EffectRecovery.of(recovery));
        }
        policy.setMaxAttempts(integer(source, TbbpmModelConstants.EFFECT_POLICY_MAX_ATTEMPTS));
        policy.setMaxReconcileAttempts(integer(source, TbbpmModelConstants.EFFECT_POLICY_MAX_RECONCILE));
        policy.setRecoveryDelay(source.getString(TbbpmModelConstants.EFFECT_POLICY_RECOVERY_DELAY));
        policy.setMaxRecoveryDuration(source.getString(TbbpmModelConstants.EFFECT_POLICY_MAX_RECOVERY_DURATION));
        return policy;
    }

    @Override
    protected void attachChildElement(Element child, EffectPolicy policy, ParseContext context) {
        if (child instanceof ReconcileAction action) {
            if (policy.getReconcileAction() != null) {
                throw new IllegalArgumentException("effectPolicy must declare at most one reconcileAction");
            }
            policy.setReconcileAction(action);
            EffectiveEffectPolicy.from(policy);
            return;
        }
        super.attachChildElement(child, policy, context);
    }

    @Override
    public String getName() {
        return TbbpmModelConstants.EFFECT_POLICY;
    }

    private static Integer integer(XmlSource source, String name) {
        String value = source.getString(name);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(name + " must be an integer", failure);
        }
    }
}
