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
package com.alibaba.compileflow.engine.process.preruntime.converter.impl;

import com.alibaba.compileflow.engine.definition.tbbpm.TbbpmModel;
import com.alibaba.compileflow.engine.process.preruntime.converter.FlowModelConverter;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.model.FlowStreamSource;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.support.tbbpm.TbbpmStreamParser;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.writer.support.tbbpm.TbbpmStreamWriter;

import java.io.OutputStream;

/**
 * @author yusu
 */
public class TbbpmModelConverter implements FlowModelConverter<TbbpmModel> {

    public static TbbpmModelConverter getInstance() {
        return TbbpmModelConverter.Holder.INSTANCE;
    }

    @Override
    public TbbpmModel convertToModel(FlowStreamSource flowStreamSource) {
        return TbbpmStreamParser.getInstance().parse(flowStreamSource);
    }

    @Override
    public OutputStream convertToStream(TbbpmModel model) {
        return TbbpmStreamWriter.getInstance().write(model);
    }

    private static class Holder {
        private static final TbbpmModelConverter INSTANCE = new TbbpmModelConverter();
    }

}
