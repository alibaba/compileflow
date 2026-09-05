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

/**
 * An enumeration of the process definition standards supported by the CompileFlow engine.
 * <p>
 * This enum is used to identify the type of a process model, which determines which
 * parser and validator implementation should be used by the engine. It is a key
 * parameter in the {@link com.alibaba.compileflow.engine.ProcessEngineFactory}.
 *
 * @author yusu
 */
public enum ProcessModelType {
    /**
     * TBBPM (Taobao Business Process Model), a proprietary process definition format.
     */
    TBBPM,
    /**
     * BPMN (Business Process Model and Notation), an industry-standard format for
     * modeling business processes.
     */
    BPMN
}
