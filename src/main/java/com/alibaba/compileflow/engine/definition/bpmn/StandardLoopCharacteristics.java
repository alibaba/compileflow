package com.alibaba.compileflow.engine.definition.bpmn;

/**
 * @author yusu
 */
public class StandardLoopCharacteristics extends LoopCharacteristics {
    private String loopCondition;
    private Boolean testBefore;
    private Long loopMaximum;

    public String getLoopCondition() {
        return loopCondition;
    }

    public void setLoopCondition(String loopCondition) {
        this.loopCondition = loopCondition;
    }

    public Boolean getTestBefore() {
        return testBefore;
    }

    public void setTestBefore(Boolean testBefore) {
        this.testBefore = testBefore;
    }

    public Long getLoopMaximum() {
        return loopMaximum;
    }

    public void setLoopMaximum(Long loopMaximum) {
        this.loopMaximum = loopMaximum;
    }
}
