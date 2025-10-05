package com.alibaba.compileflow.engine.core.observability.metrics.impl;

import com.alibaba.compileflow.engine.core.event.ProcessCoreEvents;
import com.alibaba.compileflow.engine.core.extension.ExtensionRealization;
import com.alibaba.compileflow.engine.core.observability.metrics.AbstractMetricsListener;

/**
 * Emits a structured log for every successfully completed process execution in debug mode only.
 * Production metric emission should be implemented by business-side extensions.
 * <p>
 * This listener respects the observability configuration switches and will be disabled
 * if observability is turned off.
 *
 * @author yusu
 */
@ExtensionRealization(priority = 100)
public class PerformanceMetricsListener extends AbstractMetricsListener<ProcessCoreEvents.ExecutionCompleted> {

    @Override
    public boolean doSupport(ProcessEventExtensionContext context) {
        return context != null && context.getEvent() instanceof ProcessCoreEvents.ExecutionCompleted;
    }

}
