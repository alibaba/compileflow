package com.alibaba.compileflow.engine.common.extension;

import com.alibaba.compileflow.engine.common.extension.descriptor.PluginDependencyDescriptor;

import java.util.Collections;
import java.util.List;

/**
 * @author yusu
 */
public abstract class Plugin {

    private String id;
    private String version;
    private String name;
    private String description;
    private List<PluginDependencyDescriptor> pluginDependencies = Collections.emptyList();

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

    public List<PluginDependencyDescriptor> getPluginDependencies() {
        return pluginDependencies;
    }

    public void setPluginDependencies(List<PluginDependencyDescriptor> pluginDependencies) {
        this.pluginDependencies = pluginDependencies;
    }

    public <T> boolean isEnabled(T target) {
        return true;
    }

    public List<Class<? extends ExtensionPoint>> getExtensionPointClasses() {
        return Collections.emptyList();
    }

    public List<Class<? extends ExtensionPoint>> getExtensionClasses() {
        return Collections.emptyList();
    }

    public String getExtensionPackage() {
        return this.getClass().getPackage().getName();
    }

}
