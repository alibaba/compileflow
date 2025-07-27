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
package com.alibaba.compileflow.engine.runtime.instance;

import java.util.Map;

/**
 * Represents a running process instance that can be executed.
 *
 * @author wuxiang
 * @author yusu
 */
public interface ProcessInstance extends FlowInstance {

    /**
     * Executes the process instance with the given context.
     *
     * @param context The context containing variables and metadata for the execution
     * @return A result object encapsulating the outcome of the execution
     * @throws Exception If an error occurs during the process execution
     */
    Map<String, Object> execute(Map<String, Object> context) throws Exception;

}
