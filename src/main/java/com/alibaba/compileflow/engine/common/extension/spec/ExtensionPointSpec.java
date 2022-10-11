package com.alibaba.compileflow.engine.common.extension.spec;

import com.alibaba.compileflow.engine.common.extension.IExtensionPoint;
import com.alibaba.compileflow.engine.common.extension.constant.ReducePolicy;

/**
 * @author yusu
 */
public abstract class ExtensionPointSpec extends BaseSpec {

    private String group;

    private ReducePolicy reducePolicy;

    private Class<? extends IExtensionPoint> extensionPointClass;

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

    public Class<? extends IExtensionPoint> getExtensionPointClass() {
        return extensionPointClass;
    }

    public void setExtensionPointClass(Class<? extends IExtensionPoint> extensionPointClass) {
        this.extensionPointClass = extensionPointClass;
    }

}
