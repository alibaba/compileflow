package com.allibaba.compileflow.test.om.branch_graph.calculate;

public class ResultPostProcessOp {
    public ResultPostProcessOp() {
    }

    public Integer process(Integer result) {
        Integer addNumber = 10;
        Integer finalResult = result + addNumber;
        return finalResult;
    }
}
