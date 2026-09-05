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
package com.alibaba.compileflow.engine;

import java.util.Map;

/**
 * Maps application objects to and from the engine's canonical process-variable map.
 *
 * <p>Implementations may be shared by multiple engines and must therefore be thread-safe and
 * bounded. Mapping failures fail the caller operation without fallback. The application or
 * dependency-injection container owns the mapper lifecycle; ProcessEngine never closes it.
 *
 * @author yusu
 */
public interface ProcessDataMapper {
    /**
     * Converts application input into process variables.
     *
     * <p>For {@link ProcessEngine} execution, the returned keys must be variables declared by the exact
     * Process as {@code inOutType="param"}. The canonical Map boundary performs the final
     * definition-aware validation; a mapper must not rely on extra properties being ignored.
     *
     * @param value application input value
     * @return mutable or immutable string-keyed process variables
     */
    Map<String, Object> toVariables(Object value);

    /**
     * Converts process variables into an application result type.
     *
     * @param variables  process result variables
     * @param targetType requested application result type
     * @param <T>        result type
     * @return mapped application result
     */
    <T> T fromVariables(Map<String, Object> variables, Class<T> targetType);
}
