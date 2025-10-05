package com.alibaba.compileflow.engine.core.definition.action.code;

import com.alibaba.compileflow.engine.core.definition.BaseElement;

/**
 * @author yusu
 */
public class JavaCode extends BaseElement {

    private String mode;

    private String code;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

}
