package com.allibaba.compileflow.test.om.branch_graph.calculate;

public class SqrtProcessOp {
    public SqrtProcessOp() {
    }

    public Integer process(Integer input) {
        try {
            double sqrtResult = Math.sqrt(input);
            return (int)sqrtResult;
        } catch (Exception e) {
            System.out.println("SqrtProcessOp is failed");
            return 0;
        }
    }
}
