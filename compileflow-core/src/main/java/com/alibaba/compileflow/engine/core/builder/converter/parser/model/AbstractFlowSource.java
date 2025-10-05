package com.alibaba.compileflow.engine.core.builder.converter.parser.model;

/**
 * @author yusu
 */
public abstract class AbstractFlowSource<T> implements FlowSource<T> {

    private String code;

    @Override
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

}
