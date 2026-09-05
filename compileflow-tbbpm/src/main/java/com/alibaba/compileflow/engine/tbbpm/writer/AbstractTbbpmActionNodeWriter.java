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
package com.alibaba.compileflow.engine.tbbpm.writer;

import com.alibaba.compileflow.engine.core.model.action.Action;
import com.alibaba.compileflow.engine.core.model.action.ActionType;
import com.alibaba.compileflow.engine.core.model.action.InvocationPolicy;
import com.alibaba.compileflow.engine.core.model.action.EffectPolicy;
import com.alibaba.compileflow.engine.core.model.action.ReconcileAction;
import com.alibaba.compileflow.engine.core.model.action.ReconcileInput;
import com.alibaba.compileflow.engine.tbbpm.model.ActivityNode;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModelConstants;
import javax.xml.stream.XMLStreamWriter;

/**
 * Abstract base class for TBBPM action node XML writers.
 *
 * @param <S> the action node type
 * @author yusu
 */
public abstract class AbstractTbbpmActionNodeWriter<S extends ActivityNode> extends AbstractTbbpmNodeWriter<S> {
    @Override
    protected void doWrite(S element, XMLStreamWriter xsw) throws Exception {
        xsw.writeStartElement(getName());
        writeNodeAttr(element, xsw);
        writeAction(element.getAction(), xsw);
        enrichNodeElement(element, xsw);
        writeTransition(element, xsw);
        xsw.writeEndElement();
    }

    private void writeInvocationPolicy(InvocationPolicy invocationPolicy, XMLStreamWriter xsw) throws Exception {
        if (invocationPolicy == null) {
            return;
        }
        xsw.writeStartElement(TbbpmModelConstants.INVOCATION_POLICY_ELEMENT);
        writeAttribute(xsw, TbbpmModelConstants.INVOCATION_POLICY_TIMEOUT, invocationPolicy.getTimeout());
        writeAttribute(xsw, TbbpmModelConstants.INVOCATION_POLICY_ATTEMPT_TIMEOUT, invocationPolicy.getAttemptTimeout());
        if (invocationPolicy.getMaxAttempts() != null) {
            writeAttribute(xsw, TbbpmModelConstants.INVOCATION_POLICY_MAX_ATTEMPTS,
                    String.valueOf(invocationPolicy.getMaxAttempts()));
        }
        writeAttribute(xsw, TbbpmModelConstants.INVOCATION_POLICY_INITIAL_BACKOFF, invocationPolicy.getInitialBackoff());
        if (invocationPolicy.getBackoffMultiplier() != null) {
            writeAttribute(xsw, TbbpmModelConstants.INVOCATION_POLICY_BACKOFF_MULTIPLIER,
                    String.valueOf(invocationPolicy.getBackoffMultiplier()));
        }
        writeAttribute(xsw, TbbpmModelConstants.INVOCATION_POLICY_MAX_BACKOFF, invocationPolicy.getMaxBackoff());
        if (invocationPolicy.getJitter() != null) {
            writeAttribute(xsw, TbbpmModelConstants.INVOCATION_POLICY_JITTER, invocationPolicy.getJitter().getValue());
        }
        writeAttribute(xsw, TbbpmModelConstants.INVOCATION_POLICY_RETRY_ON, invocationPolicy.getRetryOn());
        writeAttribute(xsw, TbbpmModelConstants.INVOCATION_POLICY_ON_FAILURE, invocationPolicy.getOnFailure());
        xsw.writeEndElement();
    }

    protected void writeAction(Action action, XMLStreamWriter xsw) throws Exception {
        if (action == null) {
            return;
        }
        xsw.writeStartElement(TbbpmModelConstants.ACTION);
        writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_TYPE, action.getType().getValue());
        if (action.getExecution() != null) {
            writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_EXECUTION, action.getExecution().getValue());
        }
        writeActionBody(action, xsw);
        writeInvocationPolicy(action.getInvocationPolicy(), xsw);
        writeEffectPolicy(action.getEffectPolicy(), xsw);
        xsw.writeEndElement();
    }

    private void writeEffectPolicy(EffectPolicy policy, XMLStreamWriter xsw) throws Exception {
        if (policy == null) {
            return;
        }
        xsw.writeStartElement(TbbpmModelConstants.EFFECT_POLICY);
        writeAttribute(xsw, TbbpmModelConstants.EFFECT_POLICY_RECOVERY_PLAN_VARIABLE, policy.getRecoveryPlanVariable());
        if (policy.getRecovery() != null) {
            writeAttribute(xsw, TbbpmModelConstants.EFFECT_POLICY_RECOVERY, policy.getRecovery().getValue());
        }
        writeInteger(xsw, TbbpmModelConstants.EFFECT_POLICY_MAX_ATTEMPTS, policy.getMaxAttempts());
        writeInteger(xsw, TbbpmModelConstants.EFFECT_POLICY_MAX_RECONCILE, policy.getMaxReconcileAttempts());
        writeAttribute(xsw, TbbpmModelConstants.EFFECT_POLICY_RECOVERY_DELAY, policy.getRecoveryDelay());
        writeAttribute(xsw, TbbpmModelConstants.EFFECT_POLICY_MAX_RECOVERY_DURATION, policy.getMaxRecoveryDuration());
        writeReconcileAction(policy.getReconcileAction(), xsw);
        xsw.writeEndElement();
    }

    private void writeReconcileAction(ReconcileAction action, XMLStreamWriter xsw) throws Exception {
        if (action == null) {
            return;
        }
        xsw.writeStartElement(TbbpmModelConstants.RECONCILE_ACTION);
        writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_TYPE, action.getType().getValue());
        switch (action.getType()) {
            case SPRING_BEAN -> {
                writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_BEAN, action.getBean());
                writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_CLASS, action.getClassName());
                writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_METHOD, action.getMethod());
            }
            case JAVA -> {
                writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_CLASS, action.getClassName());
                writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_METHOD, action.getMethod());
            }
            case SCRIPT -> writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_LANGUAGE, action.getLanguage());
        }
        for (ReconcileInput input : action.getInputs()) {
            xsw.writeStartElement(TbbpmModelConstants.INPUT);
            writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_SOURCE, input.getSource());
            writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_TARGET, input.getTarget());
            writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_DATA_TYPE, input.getDataType());
            xsw.writeEndElement();
        }
        if (action.getType() == ActionType.SCRIPT) {
            writeCode(xsw, action.getSource());
        }
        xsw.writeEndElement();
    }

    private void writeInteger(XMLStreamWriter xsw, String name, Integer value) throws Exception {
        if (value != null) {
            writeAttribute(xsw, name, String.valueOf(value));
        }
    }

    private void writeActionBody(Action action, XMLStreamWriter xsw) throws Exception {
        switch (action.getType()) {
            case SPRING_BEAN -> {
                writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_BEAN, action.getBean());
                writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_CLASS, action.getClassName());
                writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_METHOD, action.getMethod());
            }
            case JAVA -> {
                writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_CLASS, action.getClassName());
                writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_METHOD, action.getMethod());
            }
            case SCRIPT -> writeAttribute(xsw, TbbpmModelConstants.ATTRIBUTE_LANGUAGE, action.getLanguage());
        }
        writeMappings(action.getInputMappings(), action.getOutputMappings(), xsw, true);
        if (action.getType() == ActionType.SCRIPT) {
            writeCode(xsw, action.getSource());
        }
    }

    private void writeCode(XMLStreamWriter xsw, String code) throws Exception {
        if (code == null) {
            return;
        }
        xsw.writeStartElement(TbbpmModelConstants.CODE);
        int offset = 0;
        int marker;
        while ((marker = code.indexOf("]]>", offset)) >= 0) {
            xsw.writeCData(code.substring(offset, marker + 2));
            xsw.writeCharacters(">");
            offset = marker + 3;
        }
        xsw.writeCData(code.substring(offset));
        xsw.writeEndElement();
    }
}
