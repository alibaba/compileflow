package com.alibaba.compileflow.engine.process.preruntime.validator.impl;

import com.alibaba.compileflow.engine.definition.tbbpm.DecisionNode;
import com.alibaba.compileflow.engine.definition.tbbpm.FlowNode;
import com.alibaba.compileflow.engine.definition.tbbpm.TbbpmModel;
import com.alibaba.compileflow.engine.process.preruntime.validator.ValidateMessage;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author yusu
 */
public class TbbpmModelValidator extends AbstractFlowModelValidator<TbbpmModel>
    implements com.alibaba.compileflow.engine.process.preruntime.validator.TbbpmModelValidator {

    @Override
    public List<ValidateMessage> validate(TbbpmModel flowModel) {
        List<ValidateMessage> validateMessages = super.validate(flowModel);
        validateDecisionNode(flowModel, validateMessages);
        return validateMessages;
    }

    private void validateDecisionNode(TbbpmModel flowModel, List<ValidateMessage> validateMessages) {
        List<FlowNode> decisionNodes = flowModel.getAllNodes().stream()
            .filter(node -> node instanceof DecisionNode).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(decisionNodes)) {
            return;
        }

        for (FlowNode decisionNode : decisionNodes) {
            if (decisionNode.getIncomingTransitions().size() < 1) {
                validateMessages.add(ValidateMessage.fail(
                    "Decision Node should have at least one incoming transition, but found "
                        + decisionNode.getIncomingTransitions().size() + ", please check this decision node, id is "
                        + decisionNode.getId()));
            }
            if (decisionNode.getOutgoingTransitions().size() <= 1) {
                validateMessages.add(ValidateMessage.fail(
                    "Decision Node should have more than one outgoing transition, but found "
                        + decisionNode.getOutgoingTransitions().size() + ", please check this decision node, id is "
                        + decisionNode.getId()));
            }
        }
    }

}
