package com.alibaba.compileflow.engine.core.event;

import com.alibaba.compileflow.engine.core.extension.Extension;
import com.alibaba.compileflow.engine.core.extension.ExtensionContext;
import com.alibaba.compileflow.engine.core.extension.ExtensionPoint;

/**
 * Listener interface for process events, defined as a standard framework extension point.
 * Implementations are discovered via SPI under META-INF/extensions/.
 *
 * @param <T> The specific type of {@link ProcessEvent} this listener handles.
 * @author yusu
 */
public interface ProcessEventListener<T extends ProcessEvent> extends Extension<ProcessEventListener.ProcessEventExtensionContext> {

    String EXT_EVENT_LISTENER_CODE = "com.alibaba.compileflow.engine.core.event.ProcessEventListener.onEvent";

    /**
     * Handles the event.
     *
     * @param event The event object.
     */
    @ExtensionPoint(code = EXT_EVENT_LISTENER_CODE)
    void onEvent(T event);

    /**
     * Declares if the listener's {@code onEvent} method is to be executed asynchronously.
     */
    default boolean isAsync() {
        return true;
    }

    class ProcessEventExtensionContext implements ExtensionContext {

        private final ProcessEvent event;

        public ProcessEventExtensionContext(ProcessEvent event) {
            this.event = event;
        }

        public ProcessEvent getEvent() {
            return event;
        }

    }

}
