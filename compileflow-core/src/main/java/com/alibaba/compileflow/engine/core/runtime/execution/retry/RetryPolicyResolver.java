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
package com.alibaba.compileflow.engine.core.runtime.execution.retry;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContextHolder;
import com.alibaba.compileflow.engine.spi.execution.RetryPolicy;
import org.apache.commons.lang3.StringUtils;

/**
 * Resolves retry policy identifiers to {@link RetryPolicy} instances.
 * Built-in keywords: {@code never}, {@code transient}, {@code always}.
 * Any other value is resolved from the engine's immutable named policy map.
 *
 * @author yusu
 */
public final class RetryPolicyResolver {
    private RetryPolicyResolver() {
    }

    public static RetryPolicy resolve(String policyIdentifier) {
        if (StringUtils.isBlank(policyIdentifier) || "never".equalsIgnoreCase(policyIdentifier)) {
            return RetryPolicies.NEVER;
        }
        if ("transient".equalsIgnoreCase(policyIdentifier)) {
            return RetryPolicies.TRANSIENT;
        }
        if ("always".equalsIgnoreCase(policyIdentifier)) {
            return RetryPolicies.ALWAYS;
        }
        RetryPolicy policy = EngineExecutionContextHolder.retryPolicy(policyIdentifier);
        if (policy == null) {
            throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_003,
                    "No RetryPolicy is registered with name: " + policyIdentifier);
        }
        return policy;
    }
}
