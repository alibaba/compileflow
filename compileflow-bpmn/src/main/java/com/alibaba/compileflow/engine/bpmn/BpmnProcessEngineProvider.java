package com.alibaba.compileflow.engine.bpmn;

import com.alibaba.compileflow.engine.common.FlowModelType;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.core.AbstractProcessEngine;
import com.alibaba.compileflow.engine.core.AbstractProcessEngineProvider;

/**
 * The service provider for the BPMN 2.0 process engine.
 *
 * <p>This class is discovered and used by the {@link com.alibaba.compileflow.engine.ProcessEngineFactory}
 * to create instances of the BPMN-specific {@link BpmnProcessEngineImpl}. It follows the
 * standard Java {@link java.util.ServiceLoader} pattern.
 *
 * @author yusu
 */
public class BpmnProcessEngineProvider extends AbstractProcessEngineProvider {

    @Override
    public FlowModelType support() {
        return FlowModelType.BPMN;
    }

    @Override
    protected AbstractProcessEngine doCreateEngine(ProcessEngineConfig config) {
        return new BpmnProcessEngineImpl(config);
    }

}
