package com.allibaba.compileflow.test.om.branch_graph.calculate;

import com.allibaba.compileflow.test.om.branch_graph.common.TypeEnum;

public class TypeReadOp {
    public TypeReadOp() {
    }

    public void process(TypeEnum type) {
        if (type == TypeEnum.SCALE_SOLVE) {
            System.out.println("TypeReadOp is processed");
        }
    }
}
