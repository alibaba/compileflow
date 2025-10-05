/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.core.definition;

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.ErrorCode;

import java.util.List;

/**
 * @author wuxiang
 * @author yusu
 */
public interface NodeContainer<T extends Node> extends Node {

    List<T> getAllNodes();

    void addNode(T node);

    default T getNode(String id) {
        return getAllNodes().stream().filter(node -> id.equals(node.getId())).findFirst()
                .orElseThrow(() -> new CompileFlowException.BusinessException(ErrorCode.CF_VALIDATION_004, "Undefined node, node id is " + id));
    }

    default T getNodeByTag(String tag) {
        return getAllNodes().stream().filter(node -> tag.equals(node.getTag())).findFirst()
                .orElseThrow(() -> new CompileFlowException.BusinessException(ErrorCode.CF_VALIDATION_004, "Undefined node, node tag is " + tag));
    }

    default T getStartNode() {
        return getAllNodes().stream().filter(node -> node instanceof StartElement).findFirst()
                .orElseThrow(() -> new CompileFlowException.BusinessException(ErrorCode.CF_VALIDATION_005, "No start node found"));
    }

    default T getEndNode() {
        return getAllNodes().stream().filter(node -> node instanceof EndElement).findFirst()
                .orElseThrow(() -> new CompileFlowException.BusinessException(ErrorCode.CF_VALIDATION_005, "No end node found"));
    }

    default boolean isStateful() {
        return getAllNodes().stream().anyMatch(node -> node instanceof StatefulElement);
    }

}
