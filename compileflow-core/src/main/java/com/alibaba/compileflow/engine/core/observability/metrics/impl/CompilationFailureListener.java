package com.alibaba.compileflow.engine.core.observability.metrics.impl;

import com.alibaba.compileflow.engine.core.event.ProcessCoreEvents;
import com.alibaba.compileflow.engine.core.extension.ExtensionRealization;
import com.alibaba.compileflow.engine.core.observability.metrics.AbstractMetricsListener;

/**
 * Default listener that logs process compilation failures.
 * <p>
 * This listener is primarily intended for development and testing. It logs compilation
 * failures at the DEBUG level, ensuring that these events do not create noise in
 * production logs by default. For production environments, it is recommended to
 * register a custom listener to route these events to a dedicated monitoring or
 * alerting system.
 * <p>
 * This listener respects the observability configuration switches and will be disabled
 * if observability is turned off.
 *
 * @author yusu
 */
@ExtensionRealization(priority = 10)
public class CompilationFailureListener extends AbstractMetricsListener<ProcessCoreEvents.CompilationFailed> {

    @Override
    public boolean doSupport(ProcessEventExtensionContext context) {
        return context != null && context.getEvent() instanceof ProcessCoreEvents.CompilationFailed;
    }

}
