package com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.model;

/**
 * @author yusu <kangzhiqiang@kuaishou.com>
 * Created on 2022-12-08
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
