package com.alibaba.compileflow.engine.common.extension.spec;

import com.alibaba.compileflow.engine.common.extension.IExtensionPoint;
import com.alibaba.compileflow.engine.common.extension.consts.ReducePolicy;

/**
 * @author yusu
 */
public abstract class ExtensionSpec extends BaseSpec {

    private String scenario;

    private Integer priority;

    private String group;

    private ReducePolicy reducePolicy;

    private IExtensionPoint extension;

    public abstract boolean isCollectionFlat();

    public abstract boolean isMapFlat();

    public String getScenario() {
        return scenario;
    }

    public void setScenario(String scenario) {
        this.scenario = scenario;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public ReducePolicy getReducePolicy() {
        return reducePolicy;
    }

    public void setReducePolicy(ReducePolicy reducePolicy) {
        this.reducePolicy = reducePolicy;
    }

    public IExtensionPoint getExtension() {
        return extension;
    }

    public void setExtension(IExtensionPoint extension) {
        this.extension = extension;
    }

}
