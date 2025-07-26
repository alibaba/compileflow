package com.alibaba.compileflow.engine.extension.spec;

import com.alibaba.compileflow.engine.extension.Extension;
import com.alibaba.compileflow.engine.extension.constant.ReducePolicy;

/**
 * @author yusu
 */
public abstract class ExtensionSpec extends BaseSpec {

    private String scenario;

    private Integer priority;

    private String group;

    private ReducePolicy reducePolicy;

    private Extension extension;

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

    public Extension getExtension() {
        return extension;
    }

    public void setExtension(Extension extension) {
        this.extension = extension;
    }

}
