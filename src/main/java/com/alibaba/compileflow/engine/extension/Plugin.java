package com.alibaba.compileflow.engine.extension;

import com.alibaba.compileflow.engine.extension.descriptor.PluginDependencyDescriptor;

import java.util.Collections;
import java.util.List;

/**
 * 插件抽象基类，所有插件需继承本类。
 * <p>
 * 插件用于批量注册扩展点和扩展实现，并可声明依赖关系。
 * <p>
 * 用法示例：
 * <pre>
 * public class MyPlugin extends Plugin {
 *     // 实现必要方法
 * }
 * </pre>
 * <p>
 * 主要方法说明：
 * <ul>
 *   <li>getId()/setId()：插件唯一标识。</li>
 *   <li>getVersion()/setVersion()：插件版本。</li>
 *   <li>getName()/setName()：插件名称。</li>
 *   <li>getDescription()/setDescription()：插件描述。</li>
 *   <li>getPluginDependencies()/setPluginDependencies()：插件依赖声明。</li>
 *   <li>isEnabled(T target)：判断插件是否启用，可根据目标动态控制。</li>
 *   <li>getExtensionPointClasses()：返回本插件注册的扩展点接口列表。</li>
 *   <li>getExtensionClasses()：返回本插件注册的扩展实现类列表。</li>
 *   <li>getExtensionPackage()：返回插件扩展所在包名。</li>
 * </ul>
 * <p>
 * 插件需通过 PluginManager 进行注册和管理。
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

    public List<Class<? extends Extension>> getExtensionPointClasses() {
        return Collections.emptyList();
    }

    public List<Class<? extends Extension>> getExtensionClasses() {
        return Collections.emptyList();
    }

    public String getExtensionPackage() {
        return this.getClass().getPackage().getName();
    }

}
