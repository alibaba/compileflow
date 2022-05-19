package com.allibaba.compileflow.test.om.branch_graph.calculate;

import com.allibaba.compileflow.test.om.branch_graph.common.TypeInfo;
import com.allibaba.compileflow.test.om.branch_graph.common.TypeEnum;

public class TypeProcessOp {
    public TypeProcessOp() {
    }

    public TypeInfo process(Integer sqrtResult) {
        Integer compareNumber1 = 5;
        Integer compareNumber2 = 100;
        TypeInfo typeInfo = new TypeInfo();
        if (sqrtResult < compareNumber1) {
            typeInfo.setCalculateType(TypeEnum.ONLINE_SOLVE);
        } else if (sqrtResult > compareNumber2) {
            typeInfo.setCalculateType(TypeEnum.OFFLINE_SOLVE);
        } else {
            typeInfo.setCalculateType(TypeEnum.SCALE_SOLVE);
        }
        return typeInfo;
    }
}
