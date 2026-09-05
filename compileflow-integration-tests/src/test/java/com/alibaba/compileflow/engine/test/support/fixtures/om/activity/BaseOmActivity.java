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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public abstract class BaseOmActivity {
    private static final Logger LOGGER = LoggerFactory.getLogger(BaseOmActivity.class);

    public void execute(BusinessContext businessContext) {
        String activityName = this.getClass().getSimpleName();
        LOGGER.debug("Executing activity: {}", activityName);

        try {
            doExecute(businessContext);
            LOGGER.debug("Activity {} completed successfully", activityName);
        } catch (Exception failure) {
            LOGGER.error("Activity {} failed", activityName, failure);
            throw failure;
        }
    }

    protected abstract void doExecute(BusinessContext businessContext);
}
