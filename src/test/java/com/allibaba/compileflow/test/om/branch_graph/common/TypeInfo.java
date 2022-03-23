package com.allibaba.compileflow.test.om.branch_graph.common;

import com.allibaba.compileflow.test.om.branch_graph.common.TypeEnum;

public class TypeInfo {
    private TypeEnum calculateType;

    public TypeInfo() {
    }

    public void setCalculateType(TypeEnum type) {
        calculateType = type;
    }

    public TypeEnum getCalculateType() {
        return calculateType;
    }
}
