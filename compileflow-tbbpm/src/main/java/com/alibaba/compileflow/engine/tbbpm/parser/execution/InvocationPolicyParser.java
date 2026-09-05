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
import com.alibaba.compileflow.engine.core.model.action.InvocationPolicy;
import com.alibaba.compileflow.engine.core.model.action.EffectiveInvocationPolicy;
import com.alibaba.compileflow.engine.core.model.action.RetryJitter;
import com.alibaba.compileflow.engine.tbbpm.parser.AbstractTbbpmElementParser;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModelConstants;

/**
 * XML parser for TBBPM invocation policy elements.
 * Missing fields remain null for lossless source round-trip; semantic normalization resolves
 * defaults before target eligibility or realization.
 * This preserves parse→write round-trip fidelity.
 *
 * @author yusu
 */
public class InvocationPolicyParser extends AbstractTbbpmElementParser<InvocationPolicy> {
    private static Integer getIntegerOrNull(XmlSource xmlSource, String name) {
        String raw = xmlSource.getString(name);
        if (raw == null) {
            return null;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer invocation policy attribute '" + name + "': " + raw, e);
        }
    }

    private static Double getDoubleOrNull(XmlSource xmlSource, String name) {
        String raw = xmlSource.getString(name);
        if (raw == null) {
            return null;
        }
        try {
            if (!raw.equals(raw.trim())) {
                throw new NumberFormatException("surrounding whitespace");
            }
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid decimal invocation policy attribute '" + name + "': " + raw, e);
        }
    }

    private static RetryJitter parseRetryJitter(String raw) {
        return raw == null ? null : RetryJitter.from(raw);
    }

    @Override
    protected InvocationPolicy doParse(XmlSource xmlSource, ParseContext parseContext) throws Exception {
        InvocationPolicy invocationPolicy = new InvocationPolicy();
        invocationPolicy.setTimeout(xmlSource.getString(TbbpmModelConstants.INVOCATION_POLICY_TIMEOUT));
        invocationPolicy.setAttemptTimeout(xmlSource.getString(TbbpmModelConstants.INVOCATION_POLICY_ATTEMPT_TIMEOUT));
        invocationPolicy.setMaxAttempts(getIntegerOrNull(xmlSource, TbbpmModelConstants.INVOCATION_POLICY_MAX_ATTEMPTS));
        invocationPolicy.setInitialBackoff(xmlSource.getString(TbbpmModelConstants.INVOCATION_POLICY_INITIAL_BACKOFF));
        invocationPolicy.setBackoffMultiplier(
                getDoubleOrNull(xmlSource, TbbpmModelConstants.INVOCATION_POLICY_BACKOFF_MULTIPLIER));
        invocationPolicy.setMaxBackoff(xmlSource.getString(TbbpmModelConstants.INVOCATION_POLICY_MAX_BACKOFF));
        invocationPolicy.setJitter(parseRetryJitter(xmlSource.getString(TbbpmModelConstants.INVOCATION_POLICY_JITTER)));
        invocationPolicy.setRetryOn(xmlSource.getString(TbbpmModelConstants.INVOCATION_POLICY_RETRY_ON));
        invocationPolicy.setOnFailure(xmlSource.getString(TbbpmModelConstants.INVOCATION_POLICY_ON_FAILURE));
        EffectiveInvocationPolicy.from(invocationPolicy);
        return invocationPolicy;
    }

    @Override
    public String getName() {
        return TbbpmModelConstants.INVOCATION_POLICY_ELEMENT;
    }
}
