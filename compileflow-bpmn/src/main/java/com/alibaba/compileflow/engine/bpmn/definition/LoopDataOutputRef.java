package com.alibaba.compileflow.engine.bpmn.definition;

import com.alibaba.compileflow.engine.core.definition.BaseElement;

/**
 * @author yusu
 */
public class LoopDataOutputRef extends BaseElement {
    private String value;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
