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
package com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.provider.support;

import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.support.tbbpm.*;

/**
 * @author wuxiang
 * @author yusu
 */
public class TbbpmElementParserProvider extends AbstractFlowElementParserProvider {

    private static volatile TbbpmElementParserProvider tbbpmElementParserProvider;

    public static TbbpmElementParserProvider getInstance() {
        if (tbbpmElementParserProvider == null) {
            synchronized (TbbpmElementParserProvider.class) {
                if (tbbpmElementParserProvider == null) {
                    tbbpmElementParserProvider = new TbbpmElementParserProvider();
                    tbbpmElementParserProvider.init();
                }
            }
        }
        return tbbpmElementParserProvider;
    }

    public void init() {
        registerParser(new BpmParser());
        registerParser(new StartParser());
        registerParser(new EndParser());
        registerParser(new AutoTaskParser());
        registerParser(new ScriptTaskParser());
        registerParser(new ActionParser());
        registerParser(new ActionHandleParser());
        registerParser(new JavaActionHandleParser());
        registerParser(new SpringBeanActionHandleParser());
        registerParser(new QLActionHandleParser());
        registerParser(new MvelActionHandleParser());
        registerParser(new FlowActionHandleParser());
        registerParser(new InActionParser());
        registerParser(new OutActionParser());
        registerParser(new DecisionParser());
        registerParser(new SubBpmParser());
        registerParser(new VarParser());
        registerParser(new WaitTaskParser());
        registerParser(new WaitEventTaskParser());
        registerParser(new LoopProcessParser());
        registerParser(new ContinueParser());
        registerParser(new BreakParser());
        registerParser(new NoteParser());
        registerParser(new TransitionParser());
    }

}
