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
package com.alibaba.compileflow.engine.tbbpm.model;

import com.alibaba.compileflow.engine.core.model.Element;
import com.alibaba.compileflow.engine.core.model.variable.VariableContainer;
import com.alibaba.compileflow.engine.core.model.variable.Variable;
import java.util.ArrayList;
import java.util.List;

/**
 * Parsed TBBPM document before it is assembled into a {@link TbbpmModel}.
 *
 * <p>Populated by {@link com.alibaba.compileflow.engine.tbbpm.parser.TbbpmDocumentParser}
 * and consumed by
 * {@link com.alibaba.compileflow.engine.tbbpm.parser.TbbpmXmlParser#convertToFlowModel}.
 * Not part of the public API; use {@link TbbpmModel} for runtime access.
 *
 * @author wuxiang
 * @author yusu
 */
public class TbbpmDocument implements TbbpmNodeContainer, VariableContainer, Element {
    private final List<Variable> vars = new ArrayList<>();
    private final List<FlowNode> allNodes = new ArrayList<>();
    private String id;
    private String code;
    private String name;
    private String description;

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public List<Variable> getVariables() {
        return vars;
    }

    @Override
    public void addVariable(Variable var) {
        vars.add(var);
    }

    @Override
    public List<FlowNode> getAllNodes() {
        return allNodes;
    }

    @Override
    public void addNode(FlowNode node) {
        allNodes.add(node);
    }
}
