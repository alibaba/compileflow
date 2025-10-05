package com.alibaba.compileflow.engine.deploy;

import com.alibaba.compileflow.engine.ProcessAdminService;
import com.alibaba.compileflow.engine.deploy.detector.FlowChangeDetector;
import com.alibaba.compileflow.engine.deploy.event.FlowChangeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * Manages the hot-deployment of process flows by orchestrating change detection
 * and recompilation.
 * <p>
 * This class acts as the bridge between a {@link FlowChangeDetector} and the
 * {@link ProcessAdminService}. It subscribes to change events from the detector
 * and, in response, invokes the {@code deploy} method on the admin service.
 * This effectively updates the process definition in the running engine without
 * requiring a restart.
 * <p>
 * The use of {@code @PostConstruct} and {@code @PreDestroy} makes this class
 * easy to integrate into a dependency injection framework like Spring, ensuring
 * its lifecycle is managed automatically.
 *
 * @author yusu
 */
public class FlowHotDeployer {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlowHotDeployer.class);

    private final ProcessAdminService adminService;
    private final FlowChangeDetector detector;

    public FlowHotDeployer(ProcessAdminService adminService, FlowChangeDetector detector) {
        this.adminService = adminService;
        this.detector = detector;
    }

    /**
     * Starts the hot-deployment service.
     * <p>
     * This method is typically called automatically on bean initialization (e.g., via
     * {@code @PostConstruct}). It starts the compilationTask {@link FlowChangeDetector} and
     * registers a callback to handle incoming {@link FlowChangeEvent}s.
     */
    @PostConstruct
    public void start() {
        // The change event consumer that triggers recompilation.
        this.detector.start(changeEvent -> {
            try {
                if (changeEvent.getProcessSource() == null) {
                    LOGGER.warn("Hot deploy event skipped: ProcessSource was null.");
                    return;
                }
                // The ProcessSource provides a unified way to specify the flow to be recompiled,
                // whether the change event came from file content, a Nacos update, etc.
                LOGGER.info("Hot deployment triggered for process: code={}", changeEvent.getCode());
                adminService.deploy(changeEvent.getProcessSource());
                LOGGER.info("Hot deployment completed successfully for process: code={}", changeEvent.getCode());
            } catch (Exception e) {
                // We catch all exceptions to ensure that one failed recompilation
                // does not stop the hot-deployer from processing subsequent changes.
                LOGGER.error("Hot deployment failed for process: code={}", changeEvent.getCode(), e);
            }
        });
    }

    /**
     * Stops the hot-deployment service gracefully.
     * <p>
     * This method is typically called automatically on bean destruction (e.g., via
     * {@code @PreDestroy}). It stops the compilationTask {@link FlowChangeDetector},
     * ceasing all monitoring activities.
     */
    @PreDestroy
    public void stop() {
        try {
            LOGGER.info("Stopping hot deployment service...");
            this.detector.stop();
            LOGGER.info("Hot deployment service stopped.");
        } catch (Exception e) {
            LOGGER.error("Failed to stop hot deployment detector gracefully.", e);
        }
    }

}
