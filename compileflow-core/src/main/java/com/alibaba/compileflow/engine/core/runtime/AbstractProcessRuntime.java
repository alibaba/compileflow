/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.core.runtime;

import com.alibaba.compileflow.engine.core.definition.FlowModel;
import com.alibaba.compileflow.engine.core.runtime.instance.ProcessInstance;

/**
 * Holds the compiled process artifacts: {@link FlowModel} and instance class.
 * Lifecycle is managed by the engine.
 *
 * @author yusu
 */
public abstract class AbstractProcessRuntime<T extends FlowModel> implements ProcessRuntime<T> {

    /**
     * In-memory process definition.
     */
    protected final T flowModel;
    /**
     * Compiled class implementing the process logic.
     */
    protected final Class<? extends ProcessInstance> processInstanceClass;

    public AbstractProcessRuntime(T flowModel, Class<? extends ProcessInstance> processInstanceClass) {
        if (flowModel == null) {
            throw new IllegalArgumentException("flowModel cannot be null");
        }
        if (processInstanceClass == null) {
            throw new IllegalArgumentException("processInstanceClass cannot be null");
        }
        this.flowModel = flowModel;
        this.processInstanceClass = processInstanceClass;
    }

    /**
     * Process code.
     */
    public String getCode() {
        return flowModel.getCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T getFlowModel() {
        return flowModel;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<? extends ProcessInstance> getProcessInstanceClass() {
        return processInstanceClass;
    }

}
