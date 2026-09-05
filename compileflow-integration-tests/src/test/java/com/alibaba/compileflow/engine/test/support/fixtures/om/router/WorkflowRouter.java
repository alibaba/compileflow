/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.test.support.fixtures.om.router;

import com.alibaba.compileflow.engine.test.support.fixtures.om.context.BusinessContext;
import org.springframework.stereotype.Service;

@Service
public class WorkflowRouter {
    public boolean isReduceInventorySuccess(BusinessContext businessContext) {
        return businessContext != null && businessContext.getOrderId() != null;
    }

    public boolean isPureZero(BusinessContext businessContext) {
        if (businessContext == null) {
            return false;
        }
        Double amount = businessContext.getOrderAmount();
        return amount != null && amount == 0.0;
    }

    public String getPaymentType(BusinessContext businessContext) {
        if (businessContext == null || businessContext.getPaymentType() == null
                || businessContext.getPaymentType().isBlank()) {
            return "online";
        }
        return businessContext.getPaymentType();
    }

    public boolean isAmendmentJudgement(BusinessContext businessContext) {
        return businessContext != null;
    }

    public boolean isSecurityManualCheckPass(BusinessContext businessContext) {
        return businessContext != null && businessContext.getOrderId() != null;
    }

    public boolean isSecurityAutoCheckPass(BusinessContext businessContext) {
        return businessContext != null && businessContext.getOrderId() != null;
    }
}
