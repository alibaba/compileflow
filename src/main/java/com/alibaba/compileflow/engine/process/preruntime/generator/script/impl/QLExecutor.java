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
package com.alibaba.compileflow.engine.process.preruntime.generator.script.impl;

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.process.preruntime.generator.script.ScriptExecutor;
import com.ql.util.express.ExpressRunner;
import com.ql.util.express.IExpressContext;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yusu
 */
public class QLExecutor implements ScriptExecutor<IExpressContext> {

    private static final ExpressRunner EXPRESS_RUNNER = new ExpressRunner();

    @Override
    public Object execute(String expression, IExpressContext context) {
        List<String> errorList = new ArrayList<>();
        try {
            return EXPRESS_RUNNER.execute(expression, context, errorList,
                true, false);
        } catch (Exception e) {
            throw new CompileFlowException(errorList.toString(), e);
        }
    }

    @Override
    public String getName() {
        return "QL";
    }

}