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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Recovery-only query adapter for determining the outcome of an uncertain Effect.
 *
 * <p>A reconcile action reads only the persisted original Effect request and recovery metadata.
 * It cannot write Process state or declare execution and retry policies.
 *
 * @author yusu
 */
public final class ReconcileAction extends AbstractElement {
    private ActionType type;
    private String className;
    private String method;
    private String bean;
    private String language;
    private String source;
    private List<ReconcileInput> inputs = new ArrayList<>();

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
        return method == null ? Action.DEFAULT_METHOD : method;
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

    public List<ReconcileInput> getInputs() {
        return inputs;
    }

    public void setInputs(List<ReconcileInput> inputs) {
        this.inputs = Objects.requireNonNull(inputs, "inputs");
    }

    public void addInput(ReconcileInput input) {
        inputs.add(Objects.requireNonNull(input, "input"));
    }
}
