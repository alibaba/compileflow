package com.alibaba.compileflow.engine.tbbpm;

import com.alibaba.compileflow.engine.common.FlowModelType;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.core.AbstractProcessEngine;
import com.alibaba.compileflow.engine.core.AbstractProcessEngineProvider;

/**
 * The service provider for the TBBPM process engine.
 *
 * <p>This class is discovered and used by the {@link com.alibaba.compileflow.engine.ProcessEngineFactory}
 * to create instances of the TBBPM-specific {@link TbbpmProcessEngineImpl}. It follows the
 * standard Java {@link java.util.ServiceLoader} pattern.
 *
 * @author yusu
 */
public class TbbpmProcessEngineProvider extends AbstractProcessEngineProvider {

    @Override
    public FlowModelType support() {
        return FlowModelType.TBBPM;
    }


    @Override
    protected AbstractProcessEngine doCreateEngine(ProcessEngineConfig config) {
        return new TbbpmProcessEngineImpl(config);
    }

}
