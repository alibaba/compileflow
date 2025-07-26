package com.alibaba.compileflow.engine.definition.bpmn;

import com.alibaba.compileflow.engine.definition.common.Element;

/**
 * @author yusu
 */
public class LoopCondition implements Element {
    private String value;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

}
