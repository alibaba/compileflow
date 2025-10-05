package com.alibaba.compileflow.engine.deploy.event;

import com.alibaba.compileflow.engine.ProcessSource;
import com.alibaba.compileflow.engine.deploy.FlowHotDeployer;
import com.alibaba.compileflow.engine.deploy.detector.FlowChangeDetector;
import org.apache.commons.lang3.StringUtils;

/**
 * An immutable event object that represents a detected change in a process
 * definition.
 * <p>
 * This event is created by a {@link FlowChangeDetector} and consumed by the
 * {@link FlowHotDeployer}. It encapsulates all the necessary information for
 * the engine to re-compile the process, which can be specified via inline
 * content, a resource locator, or just by its code for a default lookup.
 *
 * @author yusu
 */
public class FlowChangeEvent {

    private final ProcessSource processSource;
    private final long time;

    private FlowChangeEvent(ProcessSource processSource) {
        if (StringUtils.isEmpty(processSource.getCode())) {
            throw new IllegalArgumentException("Process code cannot be null or empty.");
        }
        this.processSource = processSource;
        this.time = System.currentTimeMillis();
    }

    public static FlowChangeEvent of(ProcessSource processSource) {
        return new FlowChangeEvent(processSource);
    }

    public ProcessSource getProcessSource() {
        return processSource;
    }

    public long getTime() {
        return time;
    }

    public String getCode() {
        return processSource.getCode();
    }

}
