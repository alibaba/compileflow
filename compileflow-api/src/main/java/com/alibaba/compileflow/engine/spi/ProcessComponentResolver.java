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
package com.alibaba.compileflow.engine.spi;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import java.util.Objects;

/**
 * Resolves application components referenced by generated process code.
 * <p>
 * Typical implementations look up explicitly exposed Spring beans, CDI beans, or other
 * application services by name and required type. The resolver is the component-resolution
 * authority: unavailable or incompatible components must fail with a configuration exception;
 * the engine does not instantiate an arbitrary declared type as a fallback.
 * Implementations must be thread-safe. Resolved components may be invoked concurrently by
 * independent Process invocations and concurrent logical paths. A shared component must support
 * concurrent invocation, or its resolver/container must provide an isolating lifecycle or scope.
 * The application or dependency-injection container owns both contracts; Process Definitions do
 * not declare component thread safety. Resolution runs on the execution path and must remain
 * bounded; implementations that cross an external boundary must enforce their own deadline and
 * preserve interruption. Resolution failures fail the affected operation without fallback. The
 * engine never closes the resolver or resolved components.
 *
 * @author yusu
 */
public interface ProcessComponentResolver {
    /**
     * Returns a resolver that exposes no application components.
     *
     * @return disabled component resolver
     */
    static ProcessComponentResolver disabled() {
        return new ProcessComponentResolver() {
            @Override
            public <T> T resolve(String name, Class<T> requiredType) {
                Objects.requireNonNull(name, "component name must not be null");
                Objects.requireNonNull(requiredType, "required component type must not be null");
                throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_003,
                        "Process component is not available: " + name);
            }
        };
    }

    /**
     * Resolves a component by name and required type.
     *
     * @param name         component name
     * @param requiredType required component type
     * @param <T>          component type
     * @return non-null resolved component
     * @throws CompileFlowException.ConfigurationException when the component is unavailable or
     *         incompatible
     */
    <T> T resolve(String name, Class<T> requiredType);
}
