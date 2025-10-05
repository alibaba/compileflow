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
package com.alibaba.compileflow.engine.core.builder.validator;

import com.alibaba.compileflow.engine.core.definition.FlowModel;
import com.alibaba.compileflow.engine.core.extension.Extension;
import com.alibaba.compileflow.engine.core.extension.ExtensionContext;
import com.alibaba.compileflow.engine.core.extension.ExtensionPoint;

import java.util.List;

/**
 * @author yusu
 */
public interface FlowModelValidator<T extends FlowModel> extends Extension<FlowModelValidator.FlowModelValidatorExtensionContext> {

    String EXT_VALIDATE_CODE = "com.alibaba.compileflow.engine.core.builder.validator.FlowModelValidator.validate";

    @ExtensionPoint(code = EXT_VALIDATE_CODE)
    List<ValidateMessage> validate(T flowModel);

    class FlowModelValidatorExtensionContext implements ExtensionContext {
        private FlowModel flowModel;

        public FlowModelValidatorExtensionContext(FlowModel flowModel) {
            this.flowModel = flowModel;
        }

        public FlowModel getFlowModel() {
            return flowModel;
        }

    }

}
