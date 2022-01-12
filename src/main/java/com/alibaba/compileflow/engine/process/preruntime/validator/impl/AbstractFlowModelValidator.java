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
package com.alibaba.compileflow.engine.process.preruntime.validator.impl;

import com.alibaba.compileflow.engine.definition.common.*;
import com.alibaba.compileflow.engine.process.preruntime.validator.FlowModelValidator;
import com.alibaba.compileflow.engine.process.preruntime.validator.ValidateMessage;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author yusu
 */
public class AbstractFlowModelValidator<T extends FlowModel> implements FlowModelValidator<T> {

    @Override
    @SuppressWarnings("unchecked")
    public List<ValidateMessage> validate(T flowModel) {
        List<Node> nodes = flowModel.getAllNodes();
        List<ValidateMessage> validateMessages = new ArrayList<>();

        if (CollectionUtils.isEmpty(nodes)) {
            validateMessages.add(ValidateMessage
                .fail("flow model has no node, please check flow definition, code is " + flowModel.getCode()));
        }

        List<String> nodeIds = nodes.stream().map(Element::getId).collect(Collectors.toList());
        Set<String> duplicateNodeIdSet = findDuplicateNodeId(nodeIds);
        if (CollectionUtils.isNotEmpty(duplicateNodeIdSet)) {
            validateMessages.add(ValidateMessage
                .fail("flow model has duplicate node id, please check flow definition, code is " + flowModel.getCode()
                    + ", duplicate id:[" + String.join(",", duplicateNodeIdSet) + "]"));
        }

        validateStartEndNode(flowModel, validateMessages);
        return validateMessages;
    }

    @SuppressWarnings("unchecked")
    private void validateStartEndNode(T flowModel,
                                      List<ValidateMessage> validateMessages) {
        List<Node> nodes = flowModel.getAllNodes();
        List<Node> startNodes = nodes.stream().filter(node -> node instanceof StartElement).collect(
            Collectors.toList());
        if (startNodes.size() == 0) {
            validateMessages.add(ValidateMessage.fail(
                "no start node found in the flow, please check flow definition, code is " + flowModel.getCode()));
        } else if (startNodes.size() > 1) {
            validateMessages.add(ValidateMessage.fail(
                "more than one start node(node "
                    + startNodes.stream().map(Node::getId).collect(Collectors.joining(","))
                    + ") found in the flow, please check flow definition, code is " + flowModel.getCode()));
        }

        List<Node> endNodes = nodes.stream().filter(node -> node instanceof EndElement).collect(Collectors.toList());
        if (endNodes.size() == 0) {
            validateMessages.add(ValidateMessage.fail(
                "no end node found in the flow, please check flow definition, code is " + flowModel.getCode()));
        } else if (endNodes.size() > 1) {
            validateMessages.add(ValidateMessage.fail(
                "more than one end node(node "
                    + endNodes.stream().map(Node::getId).collect(Collectors.joining(","))
                    + ") found in the flow, please check flow definition, code is " + flowModel.getCode()));
        }
    }

    private Set<String> findDuplicateNodeId(List<String> nodeIds) {
        final Set<String> nodeIdSet = new HashSet<>();
        final Set<String> duplicateNodeIdSet = new HashSet<>();

        for (String id : nodeIds) {
            if (!nodeIdSet.add(id)) {
                duplicateNodeIdSet.add(id);
            }
        }
        return duplicateNodeIdSet;
    }

}
