package com.alibaba.compileflow.engine.bpmn.definition;

import com.alibaba.compileflow.engine.core.definition.ActionableNode;
import com.alibaba.compileflow.engine.core.definition.action.IAction;

/**
 * @author yusu
 */
public class ActionNode extends FlowNode implements ActionableNode<SequenceFlow> {

    private IAction action;

    @Override
    public IAction getAction() {
        return action;
    }

    @Override
    public void setAction(IAction action) {
        this.action = action;
    }


}
