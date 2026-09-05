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
package com.alibaba.compileflow.engine.core.model;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import java.util.List;
import java.util.Objects;

/**
 * Container of transition nodes in a process.
 *
 * @author yusu
 */
public interface NodeContainer<T extends TransitionNode<?>> extends Node {
    List<T> getAllNodes();

    void addNode(T node);

    default T getNode(String id) {
        String nodeId = Objects.requireNonNull(id, "id");
        return getAllNodes()
            .stream()
            .filter(node -> Objects.equals(nodeId, node.getId()))
            .findFirst()
            .orElseThrow(() -> new CompileFlowException(ErrorCode.CF_VALIDATION_004,
                    "Undefined node, node id is " + nodeId));
    }

    default T getStartNode() {
        return getAllNodes()
            .stream()
            .filter(node -> node instanceof StartElement)
            .findFirst()
            .orElseThrow(() -> new CompileFlowException(ErrorCode.CF_VALIDATION_005, "No start node found"));
    }

    default T getEndNode() {
        return getAllNodes()
            .stream()
            .filter(node -> node instanceof EndElement)
            .findFirst()
            .orElseThrow(() -> new CompileFlowException(ErrorCode.CF_VALIDATION_005, "No end node found"));
    }

    default boolean isTriggerable() {
        return getAllNodes()
            .stream()
            .anyMatch(node -> node instanceof TriggerEntryElement);
    }
}
