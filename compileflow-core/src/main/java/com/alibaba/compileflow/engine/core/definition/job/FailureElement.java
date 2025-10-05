package com.alibaba.compileflow.engine.core.definition.job;

import com.alibaba.compileflow.engine.core.definition.Element;

/**
 * @author yusu
 */
public interface FailureElement extends Element {

    /**
     * Failure handling identifier, e.g. incident/continue or bean name
     */
    String getOnFailure();

}
