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
package com.alibaba.compileflow.engine.test.support.fixtures.om.activity;

import com.alibaba.compileflow.engine.test.support.fixtures.om.context.BusinessContext;
import org.springframework.stereotype.Service;

@Service
public class WaitPaymentSuccessActivity extends BaseOmActivity {
    @Override
    protected void doExecute(BusinessContext businessContext) {
        // Wait for payment success confirmation
        businessContext.put("waitingForPayment", true);
        businessContext.put("waitStartTime", System.currentTimeMillis());
    }
}
