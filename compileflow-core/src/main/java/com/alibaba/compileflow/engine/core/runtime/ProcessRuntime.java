package com.alibaba.compileflow.engine.core.runtime;

import com.alibaba.compileflow.engine.core.definition.FlowModel;
import com.alibaba.compileflow.engine.core.runtime.instance.ProcessInstance;

/**
 * Represents a compiled process runtime that provides access to the
 * generated process instance class.
 * <p>
 * This interface serves as a bridge between the compilation phase and
 * the execution phase, encapsulating the compiled artifacts needed
 * to create and execute process instances.
 *
 * @author yusu
 */
public interface ProcessRuntime<T extends FlowModel> {

    T getFlowModel();

    /**
     * Returns the compiled process instance class.
     *
     * @return The class that implements the process logic
     */
    Class<? extends ProcessInstance> getProcessInstanceClass();

}
