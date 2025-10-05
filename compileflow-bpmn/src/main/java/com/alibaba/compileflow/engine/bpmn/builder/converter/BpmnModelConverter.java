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
package com.alibaba.compileflow.engine.bpmn.builder.converter;

import com.alibaba.compileflow.engine.bpmn.builder.converter.parser.BpmnStreamParser;
import com.alibaba.compileflow.engine.bpmn.definition.BpmnModel;
import com.alibaba.compileflow.engine.common.FlowModelType;
import com.alibaba.compileflow.engine.core.builder.converter.AbstractFlowModelConverter;
import com.alibaba.compileflow.engine.core.builder.converter.parser.model.FlowStreamSource;

import java.io.OutputStream;

/**
 * The main entry point for converting a BPMN 2.0 XML stream into a
 * {@link BpmnModel} object graph.
 * <p>
 * This class orchestrates the parsing process by delegating to a
 * {@link BpmnStreamParser}.
 *
 * @author yusu
 */
public class BpmnModelConverter extends AbstractFlowModelConverter<BpmnModel> {

    public static BpmnModelConverter getInstance() {
        return Holder.INSTANCE;
    }

    @Override
    protected FlowModelType getFlowModelType() {
        return FlowModelType.BPMN;
    }

    @Override
    public BpmnModel convertToModel(FlowStreamSource flowStreamSource) {
        return BpmnStreamParser.getInstance().parse(flowStreamSource);
    }

    /**
     * Converts a {@link BpmnModel} object graph back into a BPMN 2.0 XML stream.
     *
     * @param model The {@link BpmnModel} to convert.
     * @return An {@link OutputStream} containing the BPMN 2.0 XML.
     * @throws UnsupportedOperationException This operation is not yet implemented.
     */
    @Override
    public OutputStream convertToStream(BpmnModel model) {
        throw new UnsupportedOperationException(
                "BPMN model to XML stream conversion is not yet implemented. " +
                        "This feature requires implementing XML serialization for BpmnModel objects. " +
                        "Consider using the model's toString() method or implementing a custom XML writer."
        );
    }

    private static class Holder {
        private static final BpmnModelConverter INSTANCE = new BpmnModelConverter();
    }

}
