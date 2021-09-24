package com.alibaba.compileflow.engine.definition.tbbpm;

import com.alibaba.compileflow.engine.definition.common.WaitElement;
import com.alibaba.compileflow.engine.definition.common.action.HasInOutAction;
import com.alibaba.compileflow.engine.definition.common.action.IAction;

/**
 * @author wuxiang
 * since 2021/6/23
 **/
public class WaitEventNode extends EventNode implements WaitElement, HasInOutAction {

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
