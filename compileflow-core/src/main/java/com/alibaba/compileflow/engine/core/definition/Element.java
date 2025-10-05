/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.core.definition;

/**
 * The base marker interface for all components within a process definition model.
 * <p>
 * This interface serves as a common root for all elements that can appear in a
 * process flow, such as nodes, transitions, and even the process definition itself.
 * It provides a contract for a unique identifier, although the default implementation
 * returns "UNDEFINED". Concrete elements are expected to provide a proper ID.
 *
 * @author wuxiang
 * @author yusu
 */
public interface Element {

    /**
     * Gets the unique identifier of the element within the process definition.
     *
     * @return The element's ID, or "UNDEFINED" if not specified.
     */
    default String getId() {
        return "UNDEFINED";
    }

}
