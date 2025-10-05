package com.alibaba.compileflow.engine.bpmn.definition;

import com.alibaba.compileflow.engine.core.definition.Element;

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
