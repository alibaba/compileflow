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
package com.alibaba.compileflow.engine.process.preruntime.converter;

import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.model.FlowStreamSource;

import java.io.OutputStream;

/**
 * Interface for converting a FlowStreamSource to a model and vice versa.
 * Provides methods to convert a flow stream source into a model object and
 * to generate a stream representation of the model.

 * @param <T> The type of the converted model
 * @author yusu
 */
public interface FlowModelConverter<T> {

    /**
     * Converts a FlowStreamSource into a model object.
     *
     * @param flowStreamSource The input flow stream source
     * @return The converted model object
     */
    T convertToModel(FlowStreamSource flowStreamSource);

    /**
     * Converts a model object into an OutputStream representing the model's data.
     *
     * @param model The input model object
     * @return An OutputStream containing the serialized representation of the model
     */
    OutputStream convertToStream(T model);

}
