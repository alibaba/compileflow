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
package com.alibaba.compileflow.engine.core.model;

import com.alibaba.compileflow.engine.core.model.mapping.MappingModel;

/**
 * Declares a synchronous reference to another process.
 *
 * <p>A process call declares both its logical target and the authority used to resolve it. The
 * source-format frontend lowers the two raw selector fields into one exact semantic target.
 *
 * @author yusu
 */
public interface ProcessCallModel extends MappingModel {
    /**
     * Returns the called process code.
     *
     * @return process code
     */
    String getCalledProcessCode();

    /**
     * Returns the explicit classpath target, or {@code null}.
     */
    String getCalledProcessClasspath();

    /**
     * Returns the exact version target, or {@code null}.
     */
    String getCalledProcessVersion();
}
