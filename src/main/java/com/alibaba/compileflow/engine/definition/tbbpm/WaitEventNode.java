package com.alibaba.compileflow.engine.definition.tbbpm;

/**
 * @author wuxiang
 * since 2021/6/23
 **/
public class WaitEventNode extends EventNode {

    /**
     * 事件名称
     */
    private String eventName;

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }
}
