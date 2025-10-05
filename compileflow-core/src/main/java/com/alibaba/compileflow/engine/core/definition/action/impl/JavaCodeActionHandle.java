package com.alibaba.compileflow.engine.core.definition.action.impl;

/**
 * Represents an action defined by a snippet of raw Java code.
 * <p>
 * This handle contains a string of Java code that will be directly embedded
 * into the generated process execution class. It is suitable for short,
 * self-contained logic.
 *
 * @author yusu
 */
public class JavaCodeActionHandle extends ActionHandle {

    private String code;

    /**
     * Gets the raw Java code snippet to be executed.
     *
     * @return The Java code string.
     */
    public String getCode() {
        return code;
    }

    /**
     * Sets the raw Java code snippet.
     *
     * @param code The Java code string.
     */
    public void setCode(String code) {
        this.code = code;
    }

}
