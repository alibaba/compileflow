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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Embedded BPMN sub-process that contains its own flow elements.
 *
 * @author yusu
 */
public class SubProcess extends Activity implements BpmnElementContainer {
    private final List<AbstractFlowElement> flowElements = new ArrayList<>();

    @Override
    public List<AbstractFlowElement> getAllElements() {
        return flowElements;
    }

    @Override
    public void addElement(AbstractFlowElement element) {
        flowElements.add(element);
    }

    @Override
    public AbstractFlowElement getElement(String id) {
        return flowElements
            .stream()
            .filter(e -> Objects.equals(e.getId(), id))
            .findFirst()
            .orElseThrow(() -> new CompileFlowException(ErrorCode.CF_RESOURCE_001, "No element found, id is " + id));
    }

    @Override
    public List<FlowNode> getAllNodes() {
        return flowElements
            .stream()
            .filter(e -> e instanceof FlowNode)
            .map(e -> (FlowNode) e)
            .collect(Collectors.toList());
    }

    @Override
    public void addNode(FlowNode node) {
        flowElements.add(node);
    }
}
