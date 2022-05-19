package com.allibaba.compileflow.test.om.branch_graph.calculate;

public class OnlinePreProcessOp {
    public OnlinePreProcessOp() {
    }

    public Integer process(Integer sqrtResult) {
        Integer addNumber = 2;
        Integer onlineInput = sqrtResult + addNumber;
        return onlineInput;
    }
}
