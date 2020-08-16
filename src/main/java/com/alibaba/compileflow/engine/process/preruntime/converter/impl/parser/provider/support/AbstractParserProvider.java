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

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.Parser;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.provider.ParserProvider;

import java.util.*;

/**
 * @author wuxiang
 * @author yusu
 */
public abstract class AbstractParserProvider<T extends Parser> implements ParserProvider<T> {

    private List<Parser> parsers = new ArrayList<>();

    private Map<String, Parser> parserMap = new HashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public T getParser(String name) {
        return Optional.ofNullable((T)parserMap.get(name)).orElseThrow(
            () -> new CompileFlowException("No parser found, name is " + name));
    }

    @Override
    public void registerParser(Parser parser) {
        Parser existedParser = parserMap.get(parser.getName());
        if (existedParser != null && !existedParser.getClass().equals(parser.getClass())) {
            throw new CompileFlowException(
                "Duplicated parser name[" + parser.getName() + "] found, "
                    + "[" + parser.getClass().getName() + ", " + existedParser.getClass().getName() + "]");
        }
        parsers.add(parser);
        parserMap.put(parser.getName(), parser);
    }

}
