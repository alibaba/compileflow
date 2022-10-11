package com.alibaba.compileflow.engine.common.extension;

import com.alibaba.compileflow.engine.common.DirectedGraph;
import com.alibaba.compileflow.engine.common.Lifecycle;
import com.alibaba.compileflow.engine.common.extension.annotation.Extension;
import com.alibaba.compileflow.engine.common.extension.annotation.PluginDependency;
import com.alibaba.compileflow.engine.common.extension.annotation.PluginDependsOn;
import com.alibaba.compileflow.engine.common.extension.config.PluginConfig;
import com.alibaba.compileflow.engine.common.extension.config.PluginDependencyConfig;
import com.alibaba.compileflow.engine.common.extension.constant.PluginStatus;
import com.alibaba.compileflow.engine.common.extension.descriptor.PluginDependencyDescriptor;
import com.alibaba.compileflow.engine.common.extension.exception.PluginException;
import com.alibaba.compileflow.engine.common.extension.helper.PropertyHelper;
import com.alibaba.compileflow.engine.common.util.ClassLoaderUtils;
import com.alibaba.compileflow.engine.common.util.ClassUtils;
import com.alibaba.compileflow.engine.common.util.PackageUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author yusu
 */
public class PluginManager implements Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(PluginManager.class);

    private static final String LOAD_PLUGIN_FROM_PROPERTY = "load.plugin.from.property";
    private static final String LOAD_PLUGIN_FROM_XML = "load.plugin.from.xml";
    private static final String LOAD_PLUGIN_FROM_ANNOTATION = "load.plugin.from.annotation";
    private static final String PLUGIN_PROPERTY_FILE_NAME = "plugin.property.filename";
    private static final String DEFAULT_PLUGIN_PROPERTY_FILE_NAME = "cf-plugin.properties";
    private static final String PLUGIN_XML_FILE_NAME = "plugin.xml.filename";
    private static final String DEFAULT_PLUGIN_XML_FILE_NAME = "cf-plugin.xml";
    private static final String PLUGIN_ID_PROPERTY = "plugin.id";
    private static final String PLUGIN_VERSION_PROPERTY = "plugin.version";
    private static final String PLUGIN_NAME_PROPERTY = "plugin.name";
    private static final String PLUGIN_DESCRIPTION_PROPERTY = "plugin.description";
    private static final String PLUGIN_DEPENDSON_PROPERTY = "plugin.dependsOn";
    private static final String PLUGIN_CLASS_PROPERTY = "plugin.class";
    private static final String PLUGIN_DIRECTORY = "META-INF/plugins/";

    private static final AtomicInteger STATUS = new AtomicInteger(PluginStatus.IDLE.getCode());

    private Map<String, Plugin> pluginMap = new HashMap<>();
    private Map<String, Plugin> className2PluginMap = new HashMap<>();
    private List<Plugin> plugins = new ArrayList<>();

    public static PluginManager getInstance() {
        return Holder.INSTANCE;
    }

    @Override
    public void init() {
        if (STATUS.compareAndSet(PluginStatus.IDLE.getCode(), PluginStatus.INITIALING.getCode())) {
            initPlugins();
            STATUS.compareAndSet(PluginStatus.INITIALING.getCode(), PluginStatus.INITIALIZED.getCode());
        }
    }

    private void initPlugins() {
        loadPlugins();
        checkPluginDependency();
        registerExtensionPointsAndExtensions();
        sortExtensionsByPriority();
    }

    private void loadPlugins() {
        if (PropertyHelper.getBooleanProperty(LOAD_PLUGIN_FROM_ANNOTATION, true)) {
            loadPluginFromAnnotation();
        }
        if (PropertyHelper.getBooleanProperty(LOAD_PLUGIN_FROM_PROPERTY, true)) {
            loadPluginFromProperty();
        }
        if (PropertyHelper.getBooleanProperty(LOAD_PLUGIN_FROM_XML, true)) {
            loadPluginFromXML();
        }
    }

    private void loadPluginFromXML() {
        String pluginXmlFileName = PLUGIN_DIRECTORY + PropertyHelper.getProperty(PLUGIN_XML_FILE_NAME,
            DEFAULT_PLUGIN_XML_FILE_NAME);

        URL[] pluginXmlResourceUrls = ClassLoaderUtils.getResources(pluginXmlFileName);
        if (ArrayUtils.isEmpty(pluginXmlResourceUrls)) {
            return;
        }

        Arrays.stream(pluginXmlResourceUrls).map(this::parsePluginXml)
            .filter(Objects::nonNull)
            .forEach(this::registerPlugin);
    }

    private void loadPluginFromProperty() {
        String pluginPropertyFileName = PLUGIN_DIRECTORY + PropertyHelper.getProperty(
            PLUGIN_PROPERTY_FILE_NAME, DEFAULT_PLUGIN_PROPERTY_FILE_NAME);

        URL[] pluginPropertyResourceUrls = ClassLoaderUtils.getResources(pluginPropertyFileName);
        if (ArrayUtils.isEmpty(pluginPropertyResourceUrls)) {
            return;
        }

        Arrays.stream(pluginPropertyResourceUrls)
            .map(PropertyHelper::getProperties)
            .map(this::buildPluginConfigFromProperty)
            .forEach(this::registerPlugin);
    }

    private PluginConfig buildPluginConfigFromProperty(Properties properties) {
        PluginConfig pluginConfig = new PluginConfig();
        pluginConfig.setId(properties.getProperty(PLUGIN_ID_PROPERTY));
        pluginConfig.setVersion(properties.getProperty(PLUGIN_VERSION_PROPERTY));
        pluginConfig.setName(properties.getProperty(PLUGIN_NAME_PROPERTY));
        pluginConfig.setDescription(properties.getProperty(PLUGIN_DESCRIPTION_PROPERTY));
        pluginConfig.setPluginClass(properties.getProperty(PLUGIN_CLASS_PROPERTY));
        String pluginDependsOn = properties.getProperty(PLUGIN_DEPENDSON_PROPERTY);
        if (StringUtils.isNotEmpty(pluginDependsOn)) {
            String[] pluginDependencies = pluginDependsOn.split(",");
            if (ArrayUtils.isNotEmpty(pluginDependencies)) {
                List<PluginDependencyConfig> pluginDependencyConfigs = Arrays.stream(pluginDependencies)
                    .filter(StringUtils::isNotEmpty).map(String::trim)
                    .map(d -> {
                        String[] dependencyConfig = d.split(":");
                        if (ArrayUtils.isEmpty(dependencyConfig) || dependencyConfig.length != 2) {
                            return null;
                        }
                        PluginDependencyConfig pluginDependencyConfig = new PluginDependencyConfig();
                        pluginDependencyConfig.setPluginId(dependencyConfig[0]);
                        pluginDependencyConfig.setPluginVersion(dependencyConfig[1]);
                        return pluginDependencyConfig;
                    }).filter(Objects::nonNull).collect(Collectors.toList());
                pluginConfig.setPluginDependencyConfigs(pluginDependencyConfigs);
            }
        }
        return pluginConfig;
    }

    private void registerPlugin(PluginConfig pluginConfig) {
        if (className2PluginMap.get(pluginConfig.getPluginClass()) != null) {
            return;
        }

        if (pluginMap.get(pluginConfig.getId()) != null) {
            throw new PluginException("Plugin duplicated, id is " + pluginConfig.getId());
        }

        Object plugin = null;
        try {
            plugin = ClassUtils.newInstance(pluginConfig.getPluginClass());
        } catch (Exception e) {
            logger.error("Failed to register plugin, " + e.getMessage(), e);
        }
        if (plugin instanceof Plugin) {
            Plugin cfPlugin = (Plugin) plugin;
            cfPlugin.setId(pluginConfig.getId());
            cfPlugin.setVersion(pluginConfig.getDescription());
            cfPlugin.setName(pluginConfig.getName());
            cfPlugin.setDescription(pluginConfig.getDescription());
            cfPlugin.setPluginDependencies(
                conventDependencyConfig2Descriptor(pluginConfig.getPluginDependencyConfigs()));

            plugins.add(cfPlugin);
            pluginMap.put(cfPlugin.getId(), cfPlugin);
            className2PluginMap.put(pluginConfig.getPluginClass(), cfPlugin);
        }
    }

    private List<PluginDependencyDescriptor> conventDependencyConfig2Descriptor(
        List<PluginDependencyConfig> pluginDependencyConfigs) {
        if (CollectionUtils.isNotEmpty(pluginDependencyConfigs)) {
            return pluginDependencyConfigs.stream().map(config -> {
                PluginDependencyDescriptor pluginDependencyDescriptor = new PluginDependencyDescriptor();
                pluginDependencyDescriptor.setPluginId(config.getPluginId());
                pluginDependencyDescriptor.setPluginVersion(config.getPluginVersion());
                return pluginDependencyDescriptor;
            }).collect(Collectors.toList());
        }
        return null;
    }

    private void registerPlugin(Class<?> pluginClass) {
        if (className2PluginMap.get(pluginClass.getName()) != null) {
            return;
        }

        Plugin plugin = null;
        try {
            plugin = ClassUtils.newInstance(pluginClass);
        } catch (Exception e) {
            logger.error("Failed to register plugin, " + e.getMessage(), e);
        }
        registerPlugin(plugin);
    }

    private void registerPlugin(Plugin plugin) {
        if (plugin == null) {
            return;
        }

        com.alibaba.compileflow.engine.common.extension.annotation.Plugin pluginAnnotation = AnnotationUtils.findAnnotation(plugin.getClass(), com.alibaba.compileflow.engine.common.extension.annotation.Plugin.class);
        if (pluginAnnotation == null) {
            return;
        }

        if (pluginMap.get(pluginAnnotation.id()) != null) {
            throw new PluginException("Plugin duplicated, id is " + pluginAnnotation.id());
        }

        plugin.setId(pluginAnnotation.id());
        plugin.setVersion(pluginAnnotation.version());
        plugin.setName(pluginAnnotation.name());
        plugin.setDescription(pluginAnnotation.description());
        PluginDependsOn pluginDependsOn = pluginAnnotation.dependsOn();
        PluginDependency[] dependencies = pluginDependsOn.value();
        if (dependencies.length > 0) {
            plugin.setPluginDependencies(Arrays.stream(dependencies).map(dependency -> {
                PluginDependencyDescriptor pluginDependencyDescriptor = new PluginDependencyDescriptor();
                pluginDependencyDescriptor.setPluginId(dependency.pluginId());
                pluginDependencyDescriptor.setPluginVersion(dependency.pluginVersion());
                return pluginDependencyDescriptor;
            }).collect(Collectors.toList()));
        }

        plugins.add(plugin);
        pluginMap.put(plugin.getId(), plugin);
        className2PluginMap.put(plugin.getClass().getName(), plugin);
    }

    private void loadPluginFromAnnotation() {
        List<Class<?>> pluginClasses = PackageUtils.getAllClassInPacakge("com.alibaba.compileflow.plugin")
            .stream().filter(clazz -> clazz.isAnnotationPresent(com.alibaba.compileflow.engine.common.extension.annotation.Plugin.class))
            .collect(Collectors.toList());
        validatePlugin(pluginClasses);
        if (CollectionUtils.isNotEmpty(pluginClasses)) {
            validatePlugin(pluginClasses);
            pluginClasses.forEach(this::registerPlugin);
        }
    }

    private void validatePlugin(List<Class<?>> pluginClasses) {
        List<Class<?>> errorPluginClasses = pluginClasses.stream().filter(
            clazz -> !Plugin.class.isAssignableFrom(clazz))
            .collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(errorPluginClasses)) {
            logger.error("Plugin is not subClass of Compileflow Plugin, ["
                + errorPluginClasses.stream().map(Class::getName)
                .collect(Collectors.joining(", ")) + "]");
        }
    }

    private PluginConfig parsePluginXml(URL pluginXmlResourceUrl) {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(PluginConfig.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            return (PluginConfig) unmarshaller.unmarshal(pluginXmlResourceUrl.openStream());
        } catch (Exception e) {
            logger.error("Failed to parse plugin xml file, file is "
                + pluginXmlResourceUrl.getFile(), e);
        }
        return null;
    }

    private void checkPluginDependency() {
        if (CollectionUtils.isEmpty(plugins)) {
            return;
        }

        DirectedGraph<Plugin> pluginGraph = new DirectedGraph<>();
        for (Plugin plugin : plugins) {
            List<PluginDependencyDescriptor> pluginDependencies = plugin.getPluginDependencies();
            if (CollectionUtils.isNotEmpty(pluginDependencies)) {
                for (PluginDependencyDescriptor pluginDependency : pluginDependencies) {
                    Plugin dependencyPlugin = getDependencyPlugin(pluginDependency);
                    pluginGraph.add(DirectedGraph.Edge.of(plugin, dependencyPlugin));
                }
            }
        }

        List<Plugin> cyclicPlugins = pluginGraph.findCyclicVertexList();
        if (CollectionUtils.isNotEmpty(cyclicPlugins)) {
            throw new PluginException("Plugin has cyclic dependency, ["
                + cyclicPlugins.stream().map(Plugin::getName)
                .collect(Collectors.joining(", ")) + "]");
        }
    }

    private Plugin getDependencyPlugin(PluginDependencyDescriptor pluginDependency) {
        Plugin dependencyPlugin = pluginMap.get(pluginDependency.getPluginId());
        if (dependencyPlugin == null) {
            throw new PluginException(
                "Dependency plugin not found, please check plugin, id is " + pluginDependency.getPluginId());
        }
        return dependencyPlugin;
    }

    private void registerExtensionPointsAndExtensions() {
        if (CollectionUtils.isNotEmpty(plugins)) {
            for (Plugin plugin : plugins) {
                if (CollectionUtils.isNotEmpty(plugin.getExtensionPointClasses())) {
                    plugin.getExtensionPointClasses().forEach(this::registerExtensionPoint);
                }

                if (CollectionUtils.isNotEmpty(plugin.getExtensionClasses())) {
                    plugin.getExtensionClasses().forEach(this::registerExtension);
                }

                String extensionPackage = plugin.getExtensionPackage() != null ? plugin.getExtensionPackage()
                    : plugin.getClass().getPackage().getName();
                if (extensionPackage != null) {
                    List<Class<? extends IExtensionPoint>> extensionClasses = PackageUtils.getAllClassInPacakge(
                        extensionPackage)
                        .stream().filter(IExtensionPoint.class::isAssignableFrom)
                        .map(clazz -> (Class<? extends IExtensionPoint>) clazz)
                        .collect(Collectors.toList());
                    for (Class<? extends IExtensionPoint> extensionClass : extensionClasses) {
                        Extension extensionAnnotation = AnnotationUtils.findAnnotation(extensionClass,
                            Extension.class);
                        if (extensionAnnotation != null) {
                            registerExtension(extensionClass);
                        } else if (ClassUtils.isAbstractOrInterface(extensionClass)) {
                            registerExtensionPoint(extensionClass);
                        }
                    }
                }
            }
        }
    }

    private void registerExtensionPoint(Class<? extends IExtensionPoint> extensionPointClass) {
        ExtensionManager.getInstance().registerExtensionPoint(extensionPointClass);
    }

    private void registerExtension(Class<? extends IExtensionPoint> extensionClass) {
        ExtensionManager.getInstance().registerExtension(extensionClass);
    }

    private void sortExtensionsByPriority() {
        ExtensionManager.getInstance().sortExtensionsByPriority();
    }

    @Override
    public void stop() {
        plugins = Collections.emptyList();
        pluginMap = Collections.emptyMap();
        className2PluginMap = Collections.emptyMap();
        STATUS.set(PluginStatus.STOPPED.getCode());
    }

    private static class Holder {
        private static final PluginManager INSTANCE = new PluginManager();
    }

}
