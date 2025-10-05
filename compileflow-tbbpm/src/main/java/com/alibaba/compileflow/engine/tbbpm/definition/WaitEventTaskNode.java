package com.alibaba.compileflow.engine.tbbpm.definition;

/**
 * @author wuxiang
 * since 2021/6/23
 **/
public class WaitEventTaskNode extends StatefulNode {

    private String event;

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

}
