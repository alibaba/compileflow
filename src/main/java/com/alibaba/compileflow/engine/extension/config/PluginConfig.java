package com.alibaba.compileflow.engine.extension.config;

import javax.xml.bind.annotation.*;
import java.util.List;

/**
 * @author yusu
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class PluginConfig {

    private String id;

    private String version;

    private String name;

    private String description;

    private String pluginClass;

    @XmlElementWrapper(name = "dependsOn")
    @XmlElement(name = "dependency")
    private List<PluginDependencyConfig> pluginDependencyConfigs;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<PluginDependencyConfig> getPluginDependencyConfigs() {
        return pluginDependencyConfigs;
    }

    public void setPluginDependencyConfigs(
        List<PluginDependencyConfig> pluginDependencyConfigs) {
        this.pluginDependencyConfigs = pluginDependencyConfigs;
    }

    public String getPluginClass() {
        return pluginClass;
    }

    public void setPluginClass(String pluginClass) {
        this.pluginClass = pluginClass;
    }

}
