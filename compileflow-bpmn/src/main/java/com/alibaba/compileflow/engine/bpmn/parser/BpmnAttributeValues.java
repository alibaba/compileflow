/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.bpmn.parser;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.bpmn.model.GatewayDirection;

/**
 * Parses BPMN attribute string values into typed Java values.
 *
 * @author yusu
 */
final class BpmnAttributeValues {
    private BpmnAttributeValues() {
    }

    static boolean parseBoolean(String attributeName, String value) {
        if ("true".equals(value) || "1".equals(value)) {
            return true;
        }
        if ("false".equals(value) || "0".equals(value)) {
            return false;
        }
        throw new CompileFlowException(ErrorCode.CF_VALIDATION_002,
                "BPMN attribute '" + attributeName + "' must be one of: true, false, 1, 0", null);
    }

    static long parseInteger(String attributeName, String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException invalid) {
            throw new CompileFlowException(ErrorCode.CF_VALIDATION_002,
                    "BPMN attribute '" + attributeName + "' must be an integer: " + value, invalid);
        }
    }

    static GatewayDirection parseGatewayDirection(String value) {
        if (value == null) {
            return GatewayDirection.UNSPECIFIED;
        }
        for (GatewayDirection direction : GatewayDirection.values()) {
            if (direction.getXmlValue().equals(value)) {
                return direction;
            }
        }
        throw new CompileFlowException(ErrorCode.CF_VALIDATION_002,
                "BPMN attribute 'gatewayDirection' must be one of: Unspecified, Converging, Diverging, Mixed", null);
    }
}
