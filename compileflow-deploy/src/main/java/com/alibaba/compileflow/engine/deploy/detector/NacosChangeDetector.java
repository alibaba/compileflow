package com.alibaba.compileflow.engine.deploy.detector;

import com.alibaba.compileflow.engine.ProcessSource;
import com.alibaba.compileflow.engine.deploy.event.FlowChangeEvent;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * An implementation of {@link FlowChangeDetector} that monitors a Nacos
 * configuration center for changes to process definitions.
 * <p>
 * This detector integrates with a Nacos {@link ConfigService} to listen for updates
 * on a specified list of data IDs. When a configuration is published or changed in
 * Nacos, the registered {@link Listener} is invoked asynchronously. This detector
 * then creates a {@link FlowChangeEvent} from the new content and triggers the
 * hot-deployment callback.
 *
 * @author yusu
 */
public class NacosChangeDetector implements FlowChangeDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger(NacosChangeDetector.class);

    private final ConfigService configService;
    private final List<String> flowDataIds;
    private final String group;

    /**
     * Creates a new detector that listens for changes on a specific set of
     * Nacos configuration entries (data IDs).
     *
     * @param configService The Nacos {@link ConfigService} client, which is expected
     *                      to be managed externally (e.g., by a Spring container).
     * @param flowDataIds   A list of data IDs to monitor. Each data ID is expected
     *                      to contain the full XML/JSON content of a process definition.
     *                      The data ID is typically used as the process `code`.
     * @param group         The Nacos group in which the data IDs reside.
     */
    public NacosChangeDetector(ConfigService configService, List<String> flowDataIds, String group) {
        this.configService = configService;
        this.flowDataIds = flowDataIds;
        this.group = group;
    }

    @Override
    public void start(Consumer<FlowChangeEvent> onFlowChange) {
        if (flowDataIds == null || flowDataIds.isEmpty()) {
            LOGGER.warn("Nacos detector started, but no flow dataIds are configured to be monitored.");
            return;
        }

        LOGGER.info("Nacos detector starting: monitoring {} flows in group '{}'...",
                flowDataIds.size(), group);
        for (final String dataId : flowDataIds) {
            try {
                // To ensure flows are available on startup without waiting for a change,
                // we proactively fetch the initial content for each data ID.
                String initialContent = configService.getConfig(dataId, group, 5000);
                if (StringUtils.isNotBlank(initialContent)) {
                    LOGGER.info("Pre-loading initial content for dataId: {}", dataId);
                    onFlowChange.accept(FlowChangeEvent.of(ProcessSource.fromContent(dataId, initialContent)));
                }

                // Register an asynchronous listener for each dataId.
                Listener listener = new Listener() {
                    @Override
                    public void receiveConfigInfo(String newContent) {
                        LOGGER.info("Received Nacos config update: dataId={}", dataId);
                        onFlowChange.accept(FlowChangeEvent.of(ProcessSource.fromContent(dataId, newContent)));
                    }

                    @Override
                    public Executor getExecutor() {
                        // Returning null uses Nacos's default snapshot-cached thread pool,
                        // which is the standard and recommended approach.
                        return null;
                    }
                };

                configService.addListener(dataId, group, listener);
                LOGGER.info("Registered Nacos listener for dataId: {}", dataId);
            } catch (NacosException e) {
                LOGGER.error("Failed to register Nacos listener: dataId={}, group={}",
                        dataId, group, e);
            }
        }
    }

    @Override
    public void stop() {
        LOGGER.info("NacosChangeDetector stopped.");
        // The Nacos client's ConfigService manages its own listener lifecycle and
        // compilationTask connections. When the ConfigService is shut down (e.g., when the
        // Spring ApplicationContext is closed), it automatically handles the removal
        // of all registered listeners and closes connections.
        //
        // Therefore, an explicit removeListener call is not strictly required here
        // for typical application shutdown scenarios. This method is a no-op to
        // adhere to the interface, relying on the external management of the
        // ConfigService lifecycle.
    }
}
