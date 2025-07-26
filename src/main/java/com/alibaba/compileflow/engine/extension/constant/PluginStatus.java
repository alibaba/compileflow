package com.alibaba.compileflow.engine.extension.constant;

/**
 * @author yusu
 */
public enum PluginStatus {

    IDLE(0, "IDLE"),

    INITIALING(1, "INITIALING"),

    INITIALIZED(2, "INITIALIZED"),

    STOPPED(3, "STOPPED"),
    ;

    private int code;

    private String desc;

    PluginStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

}
