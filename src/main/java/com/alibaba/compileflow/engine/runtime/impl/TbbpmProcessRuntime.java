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
package com.alibaba.compileflow.engine.runtime.impl;

import com.alibaba.compileflow.engine.common.constants.FlowModelType;
import com.alibaba.compileflow.engine.definition.tbbpm.*;
import com.alibaba.compileflow.engine.runtime.ProcessCodeGenerator;

/**
 * @author wuxiang
 * @author yusu
 */
public class TbbpmProcessRuntime extends AbstractProcessRuntime<TbbpmModel> {

    private TbbpmProcessRuntime(TbbpmModel tbbpmModel) {
        super(tbbpmModel);
    }

    public static TbbpmProcessRuntime of(TbbpmModel tbbpmModel) {
        return new TbbpmProcessRuntime(tbbpmModel);
    }

    @Override
    public FlowModelType getFlowModelType() {
        return FlowModelType.TBBPM;
    }

    @Override
    protected boolean isBpmn20() {
        return false;
    }

    @Override
    public void init() {
        super.init();
    }

    @Override
    protected ProcessCodeGenerator getProcessCodeGenerator() {
        return new TbbpmProcessCodeGenerator(this);
    }

}
