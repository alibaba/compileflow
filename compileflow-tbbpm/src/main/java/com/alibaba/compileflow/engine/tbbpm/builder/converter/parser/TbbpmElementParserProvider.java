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
package com.alibaba.compileflow.engine.tbbpm.builder.converter.parser;

import com.alibaba.compileflow.engine.core.builder.converter.parser.provider.AbstractFlowElementParserProvider;
import com.alibaba.compileflow.engine.tbbpm.builder.converter.parser.action.*;
import com.alibaba.compileflow.engine.tbbpm.builder.converter.parser.code.ImportParser;
import com.alibaba.compileflow.engine.tbbpm.builder.converter.parser.code.ImportsParser;
import com.alibaba.compileflow.engine.tbbpm.builder.converter.parser.code.JavaCodeParser;
import com.alibaba.compileflow.engine.tbbpm.builder.converter.parser.execution.JobPolicyParser;
import com.alibaba.compileflow.engine.tbbpm.builder.converter.parser.var.VarParser;


/**
 * @author wuxiang
 * @author yusu
 */
public class TbbpmElementParserProvider extends AbstractFlowElementParserProvider {

    private static final TbbpmElementParserProvider INSTANCE = new TbbpmElementParserProvider();

    static {
        INSTANCE.init();
    }

    public static TbbpmElementParserProvider getInstance() {
        return INSTANCE;
    }

    public void init() {
        registerParser(new BpmParser());
        registerParser(new StartParser());
        registerParser(new EndParser());
        registerParser(new AutoTaskParser());
        registerParser(new ScriptTaskParser());
        registerParser(new DecisionParser());
        registerParser(new SubBpmParser());
        registerParser(new WaitTaskParser());
        registerParser(new WaitEventTaskParser());
        registerParser(new LoopProcessParser());
        registerParser(new ContinueParser());
        registerParser(new BreakParser());
        registerParser(new NoteParser());
        registerParser(new TransitionParser());
        registerParser(new ParallelParser());
        registerParser(new InclusiveParser());

        registerParser(new VarParser());
        registerParser(new JobPolicyParser());
        registerParser(new ActionParser());
        registerParser(new ActionHandleParser());
        registerParser(new JavaActionHandleParser());
        registerParser(new ImportParser());
        registerParser(new ImportsParser());
        registerParser(new JavaCodeParser());
        registerParser(new JavaSourceActionHandleParser());
        registerParser(new JavaInlineActionHandleParser());
        registerParser(new SpringBeanActionHandleParser());
        registerParser(new QLActionHandleParser());
        registerParser(new MvelActionHandleParser());
        registerParser(new ProcessActionHandleParser());
        registerParser(new InActionParser());
        registerParser(new OutActionParser());
    }

}
