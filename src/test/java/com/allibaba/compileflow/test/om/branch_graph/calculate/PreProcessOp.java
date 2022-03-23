package com.allibaba.compileflow.test.om.branch_graph.calculate;

import java.math.BigInteger;

public class PreProcessOp {
    public PreProcessOp() {
    }

    public void process(Integer input) {
        int compareNumber = 10;
        if (input < compareNumber) {
            System.out.println("input is smaller than 10");
        }
    }
}
