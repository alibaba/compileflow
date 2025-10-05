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
package com.alibaba.compileflow.engine.bpmn.builder.validator;

import com.alibaba.compileflow.engine.bpmn.definition.BpmnModel;
import com.alibaba.compileflow.engine.core.builder.validator.AbstractFlowModelValidator;
import com.alibaba.compileflow.engine.core.builder.validator.FlowModelValidator;
import com.alibaba.compileflow.engine.core.builder.validator.ValidateMessage;
import com.alibaba.compileflow.engine.core.extension.ExtensionRealization;

import java.util.List;
import java.util.Optional;

/**
 * Validator for the {@link BpmnModel}.
 * <p>
 * This class is responsible for checking the semantic and structural correctness of a
 * parsed BPMN model before it is compiled. It extends the abstract base validator
 * to inherit common validation logic and implements the {@link FlowModelValidator}
 * interface to integrate with the engine's validation extension mechanism.
 *
 * @author yusu
 */
@ExtensionRealization()
public class BpmnModelValidator extends AbstractFlowModelValidator<BpmnModel>
        implements FlowModelValidator<BpmnModel> {

    @Override
    public List<ValidateMessage> validate(BpmnModel flowModel) {
        List<ValidateMessage> validateMessages = super.validate(flowModel);
        // BPMN-specific validation logic would be added here.
        // For example, checking for disconnected sequence flows, verifying that
        // start events have no incoming flows, etc.
        return validateMessages;
    }

    @Override
    public boolean support(FlowModelValidatorExtensionContext flowModelValidatorContext) {
        return Optional.of(flowModelValidatorContext).map(FlowModelValidatorExtensionContext::getFlowModel)
                .map(e -> e instanceof BpmnModel).orElse(false);
    }

}
