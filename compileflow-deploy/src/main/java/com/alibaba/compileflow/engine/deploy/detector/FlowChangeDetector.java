package com.alibaba.compileflow.engine.deploy.detector;

import com.alibaba.compileflow.engine.deploy.FlowHotDeployer;
import com.alibaba.compileflow.engine.deploy.event.FlowChangeEvent;

import java.util.function.Consumer;

/**
 * A strategy interface for detecting changes in process flow definitions from various sources.
 * <p>
 * Implementations of this interface are responsible for monitoring a source—such as a
 * file system, a database, or a configuration center like Nacos—for any modifications
 * to flow definitions. When a change is detected, the implementation should trigger a
 * callback to notify the hot-deployment service.
 * <p>
 * This abstraction allows the hot-deployment mechanism to be independent of the
 * specific storage or management system used for process flows.
 *
 * @author yusu
 */
public interface FlowChangeDetector {

    /**
     * Initializes and starts the change detection process.
     * <p>
     * This method should set up any necessary listeners or watchers on the target
     * source. It must be idempotent; calling it multiple times should not cause errors
     * or duplicate listeners.
     *
     * @param onFlowChange A consumer callback that will be invoked with a {@link FlowChangeEvent}
     *                     whenever a change in a process definition is detected. The consuming
     *                     service (e.g., {@link FlowHotDeployer}) will then handle the event.
     */
    void start(Consumer<FlowChangeEvent> onFlowChange);

    /**
     * Stops the change detection process and cleans up any resources.
     * <p>
     * This method should release any held resources, such as file watchers, polling
     * threads, or network connections, to ensure a graceful shutdown. It must be
     * idempotent.
     */
    void stop();

}
