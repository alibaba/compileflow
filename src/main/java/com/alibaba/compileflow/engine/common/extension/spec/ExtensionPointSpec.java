package com.alibaba.compileflow.engine.common.extension.spec;

import com.alibaba.compileflow.engine.common.extension.Extension;
import com.alibaba.compileflow.engine.common.extension.constant.ReducePolicy;

/**
 * @author yusu
 */
public abstract class ExtensionPointSpec extends BaseSpec {

    private String group;

    private ReducePolicy reducePolicy;

    private Class<? extends Extension> extensionPointClass;

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

    public Class<? extends Extension> getExtensionPointClass() {
        return extensionPointClass;
    }

    public void setExtensionPointClass(Class<? extends Extension> extensionPointClass) {
        this.extensionPointClass = extensionPointClass;
    }

}
