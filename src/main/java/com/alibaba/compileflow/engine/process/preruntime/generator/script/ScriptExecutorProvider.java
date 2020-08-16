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
package com.alibaba.compileflow.engine.process.preruntime.generator.script;

import com.alibaba.compileflow.engine.common.CompileFlowException;

import java.util.*;

/**
 * @author yusu
 */
public class ScriptExecutorProvider {

    private List<ScriptExecutor> scriptExecutors = new ArrayList<>();
    private Map<String, ScriptExecutor> scriptExecutorMap = new HashMap<>();

    public static ScriptExecutorProvider getInstance() {
        return Holder.INSTANCE;
    }

    @SuppressWarnings("unchecked")
    public <T extends ScriptExecutor> T getScriptExecutor(String name) {
        return (T)Optional.ofNullable(scriptExecutorMap.get(name))
            .orElseThrow(() -> new CompileFlowException("No script executor found, name is " + name));
    }

    public void registerScriptExecutor(ScriptExecutor executor) {
        ScriptExecutor existedExecutor = scriptExecutorMap.get(executor.getName());
        if (existedExecutor != null && !existedExecutor.getClass().equals(executor.getClass())) {
            throw new CompileFlowException(
                "Duplicated executor name[" + executor.getName() + "] founded, "
                    + "[" + executor.getClass().getName() + ", " + existedExecutor.getClass().getName() + "]");
        }
        scriptExecutors.add(executor);
        scriptExecutorMap.put(executor.getName(), executor);
    }

    private static class Holder {
        private static final ScriptExecutorProvider INSTANCE = new ScriptExecutorProvider();
    }

}
