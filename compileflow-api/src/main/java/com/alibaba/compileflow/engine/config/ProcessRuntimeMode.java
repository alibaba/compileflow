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
package com.alibaba.compileflow.engine.config;

/**
 * Selects how a Process runtime realizes Process semantics.
 *
 * <p>The mode applies to both ProcessEngine and DurableProcessEngine runtime realization. It
 * changes only the local, disposable realization; Process behavior and Durable identity remain
 * unchanged.</p>
 *
 * @author yusu
 */
public enum ProcessRuntimeMode {
    /**
     * Generate and compile specialized Java.
     */
    COMPILED,
    /**
     * Interpret shared Process semantics and compile only typed Java expressions.
     */
    INTERPRETED
}
