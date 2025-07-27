package com.alibaba.compileflow.engine.process.builder.converter.impl.parser.constants;

/**
 * @author yusu
 */
public enum ActionType {

    SPRING_BEAN("spring-bean"),
    JAVA("java");

    private String type;

    ActionType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

}
