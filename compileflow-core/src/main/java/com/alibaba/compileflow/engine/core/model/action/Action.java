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
package com.alibaba.compileflow.engine.core.model.action;

import com.alibaba.compileflow.engine.core.model.AbstractElement;
import com.alibaba.compileflow.engine.core.model.mapping.InputMapping;
import com.alibaba.compileflow.engine.core.model.mapping.MappingModel;
import com.alibaba.compileflow.engine.core.model.mapping.OutputMapping;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Process action definition.
 *
 * @author yusu
 */
public final class Action extends AbstractElement implements MappingModel {
    public static final String DEFAULT_METHOD = "execute";
    private ActionType type;
    private ActionExecution execution;
    private String className;
    private String method;
    private String bean;
    private String language;
    private String source;
    private List<InputMapping> inputMappings = new ArrayList<>();
    private List<OutputMapping> outputMappings = new ArrayList<>();
    private InvocationPolicy invocationPolicy;
    private EffectPolicy effectPolicy;

    public ActionType getType() {
        return type;
    }

    public void setType(ActionType type) {
        this.type = type;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String effectiveMethod() {
        return method == null ? DEFAULT_METHOD : method;
    }

    public String getBean() {
        return bean;
    }

    public void setBean(String bean) {
        this.bean = bean;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    @Override
    public List<InputMapping> getInputMappings() {
        return inputMappings;
    }

    public void setInputMappings(List<InputMapping> inputMappings) {
        this.inputMappings = Objects.requireNonNull(inputMappings, "inputMappings");
    }

    @Override
    public List<OutputMapping> getOutputMappings() {
        return outputMappings;
    }

    public void setOutputMappings(List<OutputMapping> outputMappings) {
        this.outputMappings = Objects.requireNonNull(outputMappings, "outputMappings");
    }

    /**
     * Returns the explicitly declared Process execution semantic.
     *
     * <p>{@code null} is valid in a round-trippable source model and remains unspecified in the
     * ProcessEngine runtime semantic plan. Durable eligibility requires an explicit value before the
     * model can be deployed to a Durable target.
     */
    public ActionExecution getExecution() {
        return execution;
    }

    public void setExecution(ActionExecution execution) {
        this.execution = execution;
    }

    public InvocationPolicy getInvocationPolicy() {
        return invocationPolicy;
    }

    public void setInvocationPolicy(InvocationPolicy invocationPolicy) {
        this.invocationPolicy = invocationPolicy;
    }

    public EffectPolicy getEffectPolicy() {
        return effectPolicy;
    }

    public void setEffectPolicy(EffectPolicy effectPolicy) {
        this.effectPolicy = effectPolicy;
    }
}
