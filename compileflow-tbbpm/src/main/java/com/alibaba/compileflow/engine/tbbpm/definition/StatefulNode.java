package com.alibaba.compileflow.engine.tbbpm.definition;

import com.alibaba.compileflow.engine.core.definition.StatefulElement;
import com.alibaba.compileflow.engine.core.definition.action.HasInOutAction;
import com.alibaba.compileflow.engine.core.definition.action.IAction;

/**
 * @author yusu
 */
public abstract class StatefulNode extends FlowNode implements StatefulElement, HasInOutAction {

    private IAction inAction;

    private IAction outAction;

    @Override
    public IAction getInAction() {
        return inAction;
    }

    public void setInAction(IAction inAction) {
        this.inAction = inAction;
    }

    @Override
    public IAction getOutAction() {
        return outAction;
    }

    public void setOutAction(IAction outAction) {
        this.outAction = outAction;
    }

}
