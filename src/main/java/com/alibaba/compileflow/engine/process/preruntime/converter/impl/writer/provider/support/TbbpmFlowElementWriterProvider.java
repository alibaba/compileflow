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
package com.alibaba.compileflow.engine.process.preruntime.converter.impl.writer.provider.support;

import com.alibaba.compileflow.engine.process.preruntime.converter.impl.writer.support.tbbpm.*;

/**
 * @author yusu
 */
public class TbbpmFlowElementWriterProvider extends AbstractFlowElementWriterProvider {

    private static volatile TbbpmFlowElementWriterProvider tbbpmFlowElementWriterProvider;

    public static TbbpmFlowElementWriterProvider getInstance() {
        if (tbbpmFlowElementWriterProvider == null) {
            synchronized (TbbpmFlowElementWriterProvider.class) {
                if (tbbpmFlowElementWriterProvider == null) {
                    tbbpmFlowElementWriterProvider = new TbbpmFlowElementWriterProvider();
                    tbbpmFlowElementWriterProvider.init();
                }
            }
        }
        return tbbpmFlowElementWriterProvider;
    }

    public void init() {
        registerWriter(new NodeContainerWriter());
        registerWriter(new StartWriter());
        registerWriter(new EndWriter());
        registerWriter(new AutoTaskWriter());
        registerWriter(new ScriptTaskWriter());
        registerWriter(new WaitTaskWriter());
        registerWriter(new WaitEventTaskWriter());
        registerWriter(new DecisionWriter());
        registerWriter(new LoopProcessWriter());
        registerWriter(new ContinueWriter());
        registerWriter(new BreakWriter());
        registerWriter(new NoteWriter());
        registerWriter(new SubBpmWriter());
        registerWriter(new SpringBeanActionHandleWriter());
        registerWriter(new JavaActionHandleWriter());
        registerWriter(new ScriptActionHandleWriter());
    }

}
