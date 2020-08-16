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

import com.alibaba.compileflow.engine.definition.bpmn.*;
import com.alibaba.compileflow.engine.process.preruntime.validator.ValidateMessage;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author yusu
 */
public class BpmnModelValidator extends AbstractFlowModelValidator<BpmnModel>
    implements com.alibaba.compileflow.engine.process.preruntime.validator.BpmnModelValidator {

    @Override
    public List<ValidateMessage> validate(BpmnModel flowModel) {
        List<ValidateMessage> validateMessages = super.validate(flowModel);
        validateGatewayNode(flowModel, validateMessages);
        return validateMessages;
    }

    private void validateGatewayNode(BpmnModel flowModel, List<ValidateMessage> validateMessages) {
        List<FlowNode> gatewayNodes = flowModel.getAllNodes().stream()
            .filter(node -> node instanceof ExclusiveGateway || node instanceof InclusiveGateway
                || node instanceof EventBasedGateway)
            .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(gatewayNodes)) {
            return;
        }

        for (FlowNode gatewayNode : gatewayNodes) {
            if (gatewayNode.getIncomingFlows().size() < 1) {
                validateMessages.add(ValidateMessage.fail(
                    "Gateway node should have at least one incoming transition, but found "
                        + gatewayNode.getIncomingFlows().size() + ", please check this gateway node, id is "
                        + gatewayNode.getId()));
            }
            if (gatewayNode.getOutgoingFlows().size() <= 1) {
                validateMessages.add(ValidateMessage.fail(
                    "Gateway node should have more than one outgoing transition, but found "
                        + gatewayNode.getOutgoingFlows().size() + ", please check this gateway node, id is "
                        + gatewayNode.getId()));
            }
        }

        List<FlowNode> parallelGatewayNodes = flowModel.getAllNodes().stream()
            .filter(node -> node instanceof ParallelGateway)
            .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(parallelGatewayNodes)) {
            return;
        }

        for (FlowNode gatewayNode : gatewayNodes) {
            if (!isValidParallelGateway(gatewayNode)) {
                validateMessages.add(ValidateMessage.fail(
                    "ParallelGateway node should have one incoming transition and more than one outgoing transition "
                        + "or one outgoing transition and more than one incoming transition , but found "
                        + gatewayNode.getIncomingFlows().size() + " incoming transition and "
                        + gatewayNode.getOutgoingFlows().size()
                        + " outgoing transition, please check this gateway node, id is "
                        + gatewayNode.getId()));
            }
        }
    }

    private boolean isValidParallelGateway(FlowNode gatewayNode) {
        return (gatewayNode.getIncomingFlows().size() == 1 && gatewayNode.getOutgoingFlows().size() > 1)
            || (gatewayNode.getOutgoingFlows().size() == 1 && gatewayNode.getIncomingFlows().size() > 1);
    }

}
