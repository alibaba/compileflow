package com.alibaba.compileflow.engine.core.extension;

/**
 * @author yusu
 * Created on 2022-02-17
 */
public class ExtensionRealizationSpec {

    private int priority;

    private Extension target;

    public static ExtensionRealizationSpec of(ExtensionRealization extensionRealizationAnnotation, Extension extension) {
        ExtensionRealizationSpec extensionSpec = new ExtensionRealizationSpec();
        extensionSpec.setPriority(extensionRealizationAnnotation.priority());
        extensionSpec.setTarget(extension);
        return extensionSpec;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public Extension getTarget() {
        return target;
    }

    public void setTarget(Extension target) {
        this.target = target;
    }

}
