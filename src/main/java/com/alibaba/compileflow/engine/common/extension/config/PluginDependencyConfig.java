package com.alibaba.compileflow.engine.common.extension.config;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author yusu
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class PluginDependencyConfig {

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
