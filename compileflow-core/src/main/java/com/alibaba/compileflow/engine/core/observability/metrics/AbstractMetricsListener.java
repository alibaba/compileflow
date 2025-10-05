package com.alibaba.compileflow.engine.core.observability.metrics;

import com.alibaba.compileflow.engine.config.ProcessPropertyProvider;
import com.alibaba.compileflow.engine.core.event.ProcessEvent;
import com.alibaba.compileflow.engine.core.event.ProcessEventListener;
import org.slf4j.Logger;

/**
 * @author yusu
 */
public abstract class AbstractMetricsListener<T extends ProcessEvent.AbstractProcessEvent> implements ProcessEventListener<T> {

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(AbstractMetricsListener.class);

    @Override
    public void onEvent(T event) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("METRIC_EVENT: type={}, processCode={}, durationMs={}, errorMessage={}",
                    event.getEventType(),
                    event.getProcessCode(),
                    event.getContext().getDurationMs(),
                    event.getContext().getErrorMessage());
        }
    }

    @Override
    public boolean isAsync() {
        return ProcessPropertyProvider.Observability.isEventsAsync();
    }

    @Override
    public boolean support(ProcessEventExtensionContext context) {
        return ProcessPropertyProvider.Observability.isMetricsEnabled() && doSupport(context);
    }

    protected abstract boolean doSupport(ProcessEventExtensionContext context);

}
