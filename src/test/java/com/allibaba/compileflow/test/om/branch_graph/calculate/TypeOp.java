package com.allibaba.compileflow.test.om.branch_graph.calculate;

import com.allibaba.compileflow.test.om.branch_graph.common.TypeInfo;
import com.allibaba.compileflow.test.om.branch_graph.common.TypeEnum;

public class TypeOp {
    public TypeOp() {
    }

    public TypeEnum process(TypeInfo typeInfo) {
        return typeInfo.getCalculateType();
    }
}
