package com.alibaba.compileflow.engine.core.definition.action.code;

import com.alibaba.compileflow.engine.core.definition.BaseElement;

/**
 * @author yusu
 */
public class Import extends BaseElement {

    private String value;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

}
