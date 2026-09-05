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
package com.alibaba.compileflow.engine.bpmn.validation;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.bpmn.model.MultiInstanceLoopCharacteristics;
import com.alibaba.compileflow.engine.bpmn.model.StandardLoopCharacteristics;
import com.alibaba.compileflow.engine.core.java.naming.JavaNames;
import com.alibaba.compileflow.engine.core.semantic.naming.ProcessNames;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/**
 * Validates the executable subset of BPMN loop characteristics.
 *
 * @author yusu
 */
public final class BpmnLoopContract {
    private BpmnLoopContract() {
    }

    public static void validate(MultiInstanceLoopCharacteristics loop) {
        requireJavaIdentifier("cf:collection", loop.getCollection());
        requireJavaIdentifier("cf:item", loop.getItem());
        validateOptionalJavaIdentifier("cf:index", loop.getIndex());
        validateOptionalJavaIdentifier("cf:target", loop.getTarget());
        validateOptionalJavaIdentifier("cf:source", loop.getSource());
        if (loop.getItemType() != null) {
            if (StringUtils.isBlank(loop.getItemType())) {
                throw invalid("cf:itemType must not be blank when declared");
            }
            if (!JavaNames.isClassName(loop.getItemType())) {
                throw invalid("cf:itemType must be a valid Java class name: " + loop.getItemType());
            }
        }
        if (loop.getIndex() != null && Objects.equals(loop.getItem(), loop.getIndex())) {
            throw invalid("cf:item and cf:index must use different names");
        }
        if (StringUtils.isBlank(loop.getTarget()) != StringUtils.isBlank(loop.getSource())) {
            throw invalid("cf:target and cf:source must be declared together");
        }
        if (StringUtils.isNotBlank(loop.getTarget()) && Objects.equals(loop.getTarget(), loop.getSource())) {
            throw invalid("cf:target and cf:source must use different names");
        }
    }

    public static void validate(StandardLoopCharacteristics loop) {
        Long loopMaximum = loop.getLoopMaximum();
        if (loopMaximum != null && loopMaximum <= 0) {
            throw invalid("BPMN standard loopMaximum must be greater than zero");
        }
        if (loopMaximum != null && loopMaximum > Integer.MAX_VALUE) {
            throw invalid("BPMN standard loopMaximum must not exceed " + Integer.MAX_VALUE);
        }
        if (loop.getLoopCondition() != null && StringUtils.isBlank(loop.getLoopCondition())) {
            throw invalid("BPMN standard loopCondition must not be blank when declared");
        }
        if (loopMaximum == null && StringUtils.isBlank(loop.getLoopCondition())) {
            throw invalid("BPMN standard loop requires loopCondition or loopMaximum");
        }
    }

    private static void requireJavaIdentifier(String attribute, String value) {
        if (StringUtils.isBlank(value)) {
            throw invalid("BPMN multi-instance execution requires " + attribute);
        }
        validateJavaIdentifier(attribute, value);
    }

    private static void validateOptionalJavaIdentifier(String attribute, String value) {
        if (value == null) {
            return;
        }
        if (StringUtils.isBlank(value)) {
            throw invalid(attribute + " must not be blank when declared");
        }
        validateJavaIdentifier(attribute, value);
    }

    private static void validateJavaIdentifier(String attribute, String value) {
        if (!ProcessNames.isIdentifier(value)) {
            throw invalid(attribute + " must be a valid Java identifier: " + value);
        }
        if (ProcessNames.isReserved(value)) {
            throw invalid(attribute + " uses the reserved CompileFlow identifier prefix: " + value);
        }
    }

    private static CompileFlowException invalid(String message) {
        return new CompileFlowException(ErrorCode.CF_VALIDATION_002, message, null);
    }
}
