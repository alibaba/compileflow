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
package com.alibaba.compileflow.engine.bpmn.model;

import com.alibaba.compileflow.engine.core.model.mapping.InputMapping;
import com.alibaba.compileflow.engine.core.model.mapping.MappingModel;
import com.alibaba.compileflow.engine.core.model.mapping.OutputMapping;
import com.alibaba.compileflow.engine.core.model.action.ActionExecution;
import com.alibaba.compileflow.engine.core.model.action.EffectPolicy;
import com.alibaba.compileflow.engine.core.model.action.InvocationPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Executes a script expression as a BPMN task.
 *
 * @author yusu
 */
public class ScriptTask extends Activity implements MappingModel {
    private final List<InputMapping> inputMappings = new ArrayList<>();
    private final List<OutputMapping> outputMappings = new ArrayList<>();
    private String script;
    private String scriptFormat;
    private ActionExecution execution = ActionExecution.REPLAYABLE;
    private InvocationPolicy invocationPolicy;
    private EffectPolicy effectPolicy;

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public String getScriptFormat() {
        return scriptFormat;
    }

    public void setScriptFormat(String scriptFormat) {
        this.scriptFormat = scriptFormat;
    }

    public ActionExecution getExecution() {
        return execution;
    }

    public void setExecution(ActionExecution execution) {
        this.execution = Objects.requireNonNull(execution, "execution");
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

    @Override
    public List<InputMapping> getInputMappings() {
        return inputMappings;
    }

    @Override
    public List<OutputMapping> getOutputMappings() {
        return outputMappings;
    }
}
