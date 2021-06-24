package com.allibaba.compileflow.test.om.router;

import com.allibaba.compileflow.test.om.context.BusinessContext;
import org.springframework.stereotype.Service;

/**
 * @author yusu
 */
@Service
public class WorkFlowRouter {

    public boolean isReduceInventorySuccess(BusinessContext businessContext) {
        return true;
    }

    public boolean isPureZero(BusinessContext businessContext) {
        return true;
    }

    public String getPaymentType(BusinessContext businessContext) {
        return "online";
    }

    public boolean amendmentJudgement(BusinessContext businessContext) {
        return true;
    }

    public boolean isSecurityManualCheckPass(BusinessContext businessContext) {
        return true;
    }

    public boolean isSecurityAutoCheckPass(BusinessContext businessContext) {
        return true;
    }

}