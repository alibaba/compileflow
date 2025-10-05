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
package com.alibaba.compileflow.engine.core.builder.converter.parser.provider;

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.ErrorCode;
import com.alibaba.compileflow.engine.core.builder.converter.parser.Parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author wuxiang
 * @author yusu
 */
public abstract class AbstractParserProvider<T extends Parser> implements ParserProvider<T> {

    private final Map<String, Parser> parserMap = new ConcurrentHashMap<>();
    private List<Parser> parsers = new ArrayList<>();

    @Override
    @SuppressWarnings("unchecked")
    public T getParser(String name) {
        return Optional.ofNullable((T) parserMap.get(name)).orElseThrow(
                () -> new CompileFlowException.ConfigurationException(
                        ErrorCode.CF_CONFIG_003,
                        "No parser found, name is " + name
                ));
    }

    @Override
    public void registerParser(Parser parser) {
        Parser oldParser = parserMap.putIfAbsent(parser.getName(), parser);
        if (oldParser != null) {
            if (!oldParser.getClass().equals(parser.getClass())) {
                throw new CompileFlowException.ConfigurationException(
                        ErrorCode.CF_CONFIG_001,
                        "Duplicated parser name[" + parser.getName() + "] found, "
                                + "[" + parser.getClass().getName() + ", " + oldParser.getClass().getName() + "]",
                        null
                );
            }
            return;
        }
        parsers.add(parser);
    }

}
