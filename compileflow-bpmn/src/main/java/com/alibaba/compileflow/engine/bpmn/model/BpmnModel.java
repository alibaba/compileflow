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

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.core.model.AbstractFlowElement;
import com.alibaba.compileflow.engine.core.model.FlowModel;
import com.alibaba.compileflow.engine.core.model.Element;
import com.alibaba.compileflow.engine.core.model.variable.Variable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * In-memory object representation of a BPMN process definition.
 *
 * <p>Root of the object graph created by
 * {@link com.alibaba.compileflow.engine.bpmn.BpmnModelReader}
 * when parsing a BPMN definition.
 *
 * @author yusu
 */
public class BpmnModel implements FlowModel<FlowNode> {
    private final Process process;
    private List<Message> messages = new ArrayList<>();
    private String definitionsId;
    private String targetNamespace;
    private String typeLanguage;
    private String expressionLanguage;

    public BpmnModel(Process process) {
        this.process = Objects.requireNonNull(process, "process");
    }

    @Override
    public String getId() {
        return process.getId();
    }

    @Override
    public String getCode() {
        return process.getId();
    }

    @Override
    public String getName() {
        return process.getName();
    }

    @Override
    public List<Variable> getVariables() {
        return process.getVariables();
    }

    private static Stream<AbstractFlowElement> descendants(List<AbstractFlowElement> elements) {
        return elements.stream().flatMap(element -> {
            if (element instanceof SubProcess subProcess) {
                return Stream.concat(Stream.of(element), descendants(subProcess.getAllElements()));
            }
            return Stream.of(element);
        });
    }

    public String getDefinitionsId() {
        return definitionsId;
    }

    public void setDefinitionsId(String definitionsId) {
        this.definitionsId = definitionsId;
    }

    public String getTargetNamespace() {
        return targetNamespace;
    }

    public void setTargetNamespace(String targetNamespace) {
        this.targetNamespace = targetNamespace;
    }

    public String getTypeLanguage() {
        return typeLanguage;
    }

    public void setTypeLanguage(String typeLanguage) {
        this.typeLanguage = typeLanguage;
    }

    public String getExpressionLanguage() {
        return expressionLanguage;
    }

    public void setExpressionLanguage(String expressionLanguage) {
        this.expressionLanguage = expressionLanguage;
    }

    public Process getProcess() {
        return process;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public Element getFlowElement(String id) {
        return Stream
            .concat(descendants(process.getFlowElements()), messages.stream())
            .filter(Objects::nonNull)
            .filter(element -> Objects.equals(element.getId(), id))
            .findFirst()
            .orElseThrow(() -> new CompileFlowException(ErrorCode.CF_RESOURCE_001,
                    "Undefined element, element id is " + id));
    }

    public <T extends Element> T getFlowElement(String id, Class<T> elementType) {
        Objects.requireNonNull(elementType, "elementType");
        Element element = getFlowElement(id);
        if (!elementType.isInstance(element)) {
            throw new CompileFlowException(ErrorCode.CF_VALIDATION_002,
                    "Element " + id + " has type " + element.getClass().getName() + ", expected " + elementType.getName(),
                    null);
        }
        return elementType.cast(element);
    }

    @Override
    public List<FlowNode> getAllNodes() {
        return process.getAllNodes();
    }

    @Override
    public void addNode(FlowNode node) {
        getProcess().addNode(node);
    }
}
