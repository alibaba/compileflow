package com.alibaba.compileflow.engine.bpmn.runtime;

import com.alibaba.compileflow.engine.bpmn.definition.BpmnModel;
import com.alibaba.compileflow.engine.core.runtime.AbstractProcessRuntime;
import com.alibaba.compileflow.engine.core.runtime.instance.ProcessInstance;

/**
 * BPMN-specific process runtime implementation.
 * This class is part of the BPMN module and should not be referenced from core.
 *
 * @author yusu
 */
public class BpmnProcessRuntime extends AbstractProcessRuntime<BpmnModel> {

    public BpmnProcessRuntime(BpmnModel flowModel, Class<? extends ProcessInstance> processInstanceClass) {
        super(flowModel, processInstanceClass);
    }

}
