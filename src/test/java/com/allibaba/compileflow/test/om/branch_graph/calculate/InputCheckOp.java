package com.allibaba.compileflow.test.om.branch_graph.calculate;

public class InputCheckOp {
    public InputCheckOp() {
    }

    public Boolean process(Integer input) {
        Integer compareNumber = 20;
        if (input > compareNumber) {
            return false;
        } else {
            return true;
        }
    }
}
