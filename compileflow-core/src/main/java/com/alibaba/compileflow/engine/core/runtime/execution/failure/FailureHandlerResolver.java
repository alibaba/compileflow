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
package com.alibaba.compileflow.engine.core.runtime.execution.failure;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.core.runtime.context.EngineExecutionContextHolder;
import com.alibaba.compileflow.engine.spi.execution.FailureHandler;
import org.apache.commons.lang3.StringUtils;

/**
 * Resolves failure handler identifiers to {@link FailureHandler} instances.
 * Built-in keywords: {@code propagate} (throw to the caller), {@code continue} (skip and proceed).
 * Any other value is resolved from the engine's immutable named handler map.
 *
 * @author yusu
 */
public final class FailureHandlerResolver {
    private FailureHandlerResolver() {
    }

    public static FailureHandler resolve(String handlerIdentifier) {
        if (StringUtils.isBlank(handlerIdentifier)) {
            return FailureHandlers.PROPAGATE;
        }

        if ("propagate".equalsIgnoreCase(handlerIdentifier)) {
            return FailureHandlers.PROPAGATE;
        }
        if ("continue".equalsIgnoreCase(handlerIdentifier)) {
            return FailureHandlers.CONTINUE;
        }

        FailureHandler handler = EngineExecutionContextHolder.failureHandler(handlerIdentifier);
        if (handler == null) {
            throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_003,
                    "No FailureHandler is registered with name: " + handlerIdentifier);
        }
        return handler;
    }
}
