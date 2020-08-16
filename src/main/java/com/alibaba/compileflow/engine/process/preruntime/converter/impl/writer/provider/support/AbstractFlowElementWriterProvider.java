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

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.definition.common.Element;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.writer.FlowElementWriter;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.writer.provider.FlowElementWriterProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author wuxiang
 * @author yusu
 */
public abstract class AbstractFlowElementWriterProvider implements FlowElementWriterProvider {

    protected List<FlowElementWriter> writers = new ArrayList<>();

    protected Map<Class<? extends Element>, FlowElementWriter> writerMap = new HashMap<>();

    @Override
    public FlowElementWriter getWriter(Class<? extends Element> clazz) {
        FlowElementWriter writer = writerMap.get(clazz);
        if (writer == null) {
            throw new CompileFlowException("No writer found, name is " + clazz.getName());
        }
        return writer;
    }

    @Override
    public void registerWriter(FlowElementWriter writer) {
        writers.add(writer);
        writerMap.put(writer.getElementClass(), writer);
    }

}
