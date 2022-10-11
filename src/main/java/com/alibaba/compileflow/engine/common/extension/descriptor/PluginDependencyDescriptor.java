package com.alibaba.compileflow.engine.common.extension.descriptor;

/**
 * @author yusu
 */
public class PluginDependencyDescriptor {

    private String pluginId;

    private String pluginVersion;

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public String getPluginVersion() {
        return pluginVersion;
    }

    public void setPluginVersion(String pluginVersion) {
        this.pluginVersion = pluginVersion;
    }

}
