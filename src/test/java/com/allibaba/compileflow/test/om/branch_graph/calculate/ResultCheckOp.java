package com.allibaba.compileflow.test.om.branch_graph.calculate;

public class ResultCheckOp {
    public ResultCheckOp() {
    }

    public Boolean process(Integer result) {
        Integer compareNumber = 10;
        if (result < compareNumber) {
            return true;
        }
        return false;
    }
}
