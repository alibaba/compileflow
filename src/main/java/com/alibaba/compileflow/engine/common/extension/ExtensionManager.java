package com.alibaba.compileflow.engine.common.extension;

import com.alibaba.compileflow.engine.common.Lifecycle;
import com.alibaba.compileflow.engine.common.MultiMap;
import com.alibaba.compileflow.engine.common.extension.annotation.Extension;
import com.alibaba.compileflow.engine.common.extension.annotation.ExtensionPoint;
import com.alibaba.compileflow.engine.common.extension.spec.*;
import com.alibaba.compileflow.engine.common.util.ClassLoaderUtils;
import com.alibaba.compileflow.engine.common.util.ClassUtils;
import com.alibaba.compileflow.engine.common.util.MethodUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author yusu
 */
public class ExtensionManager implements Lifecycle {

    private static final String EXTENSION_DIRECTORY = "META-INF/extensions/";

    private static final Logger logger = LoggerFactory.getLogger(ExtensionManager.class);

    private Set<Class<? extends IExtensionPoint>> extensionPointClasses = new HashSet<>();
    private Set<Class<? extends IExtensionPoint>> extensionsClasses = new HashSet<>();

    private List<ExtensionPointSpec> extensionPointSpecs = new ArrayList<>();
    private Map<String, ExtensionPointSpec> extensionPointSpecMap = new HashMap<>();
    private List<ExtensionSpec> extensionSpecs = new ArrayList<>();
    private MultiMap<String, ExtensionSpec> extensionSpecMap = new MultiMap<>();

    public static ExtensionManager getInstance() {
        return Holder.INSTANCE;
    }

    @Override
    public void init() {
        loadExtension();
    }

    public ExtensionPointSpec getExtensionPoint(String extensionCode) {
        return extensionPointSpecMap.get(extensionCode);
    }

    public List<ExtensionSpec> getExtensions(String extensionCode) {
        return extensionSpecMap.get(extensionCode);
    }

    public <T extends IExtensionPoint> List<T> getExtensions(String extensionCode, Class<T> extensionClass) {
        List<ExtensionSpec> extensionSpecs = extensionSpecMap.get(extensionCode);
        if (CollectionUtils.isEmpty(extensionSpecs)) {
            return Collections.emptyList();
        }
        return extensionSpecs.stream().map(ExtensionSpec::getExtension)
            .filter(extension -> extensionClass.isAssignableFrom(extension.getClass()))
            .map(extension -> (T) extension).collect(Collectors.toList());
    }

    public void registerExtensionPoint(Class<? extends IExtensionPoint> extensionPointClass) {
        if (extensionPointClasses.contains(extensionPointClass)) {
            return;
        }

        ExtensionPoint extensionPointAnnotation = AnnotationUtils.findAnnotation(extensionPointClass, ExtensionPoint.class);
        if (extensionPointAnnotation != null) {
            ExtensionPointSpec extensionPointSpec = ClassExtensionPointSpec.of(extensionPointAnnotation,
                extensionPointClass);
            extensionPointSpecs.add(extensionPointSpec);
            extensionPointSpecMap.put(extensionPointSpec.getCode(), extensionPointSpec);
        }

        Method[] methods = extensionPointClass.getMethods();
        for (Method method : methods) {
            extensionPointAnnotation = AnnotationUtils.findAnnotation(method, ExtensionPoint.class);
            if (extensionPointAnnotation != null) {
                ExtensionPointSpec extensionPointSpec = MethodExtensionPointSpec.of(extensionPointAnnotation,
                    extensionPointClass, method);
                extensionPointSpecs.add(extensionPointSpec);
                extensionPointSpecMap.put(extensionPointSpec.getCode(), extensionPointSpec);
            }
            Class<?> returnType = method.getReturnType();
            if (IExtensionPoint.class.isAssignableFrom(returnType)) {
                registerExtensionPoint((Class<? extends IExtensionPoint>) returnType);
            }
        }
    }

    public void registerExtensions(Class<? extends IExtensionPoint> extensionsClass) {
        IExtensionPoint extensions = null;
        try {
            extensions = ClassUtils.newInstance(extensionsClass);
        } catch (Exception e) {
            logger.error("Failed to register extensions, " + e.getMessage(), e);
        }
        if (extensions != null) {
            registerExtensions(extensions);
        }
    }

    public void sortExtensionsByPriority() {
        if (extensionSpecMap != null) {
            extensionSpecMap.values().forEach(this::sortExtensionMethod);
        }
    }

    private void registerExtensions(IExtensionPoint extensions) {
        Class<? extends IExtensionPoint> extensionsClass = extensions.getClass();
        if (extensionsClasses.contains(extensionsClass)) {
            return;
        }

        Extension extensionAnnotation = extensionsClass.getAnnotation(Extension.class);
        if (extensionAnnotation == null) {
            return;
        }

        ExtensionPoint extensionPointAnnotation = AnnotationUtils.findAnnotation(extensionsClass, ExtensionPoint.class);
        if (extensionPointAnnotation != null) {
            ExtensionSpec extensionSpec = ClassExtensionSpec.of(extensionAnnotation,
                extensionPointAnnotation, extensions);
            extensionSpecs.add(extensionSpec);
            extensionSpecMap.put(extensionSpec.getCode(), extensionSpec);
        }

        Method[] methods = extensionsClass.getMethods();
        for (Method method : methods) {
            extensionPointAnnotation = AnnotationUtils.findAnnotation(method, ExtensionPoint.class);
            if (extensionPointAnnotation != null) {
                ExtensionSpec extensionSpec = MethodExtensionSpec.of(extensionAnnotation,
                    extensionPointAnnotation, extensions, method);
                extensionSpecs.add(extensionSpec);
                extensionSpecMap.put(extensionSpec.getCode(), extensionSpec);
            }
            Class<?> returnType = method.getReturnType();
            if (IExtensionPoint.class.isAssignableFrom(returnType)) {
                IExtensionPoint subExtensions = null;
                try {
                    subExtensions = MethodUtils.invoke(extensions, method);
                } catch (Exception e) {
                    logger.error("Failed to get subExtension, method is " + method.getName(), e);
                }
                if (subExtensions != null) {
                    registerExtensions(subExtensions);
                }
            }
        }
    }

    private void sortExtensionMethod(List<ExtensionSpec> extensionSpecs) {
        extensionSpecs.sort(Comparator.comparing(ExtensionSpec::getPriority).reversed());
    }

    private void loadExtension() {
        URL[] extensionDirUrls = ClassLoaderUtils.getResources(EXTENSION_DIRECTORY);
        if (ArrayUtils.isEmpty(extensionDirUrls)) {
            return;
        }

        for (URL extensionDirUrl : extensionDirUrls) {
            try {
                String filePath = URLDecoder.decode(extensionDirUrl.getFile(), "UTF-8");
                File dir = new File(filePath);
                if (!dir.exists() || !dir.isDirectory()) {
                    return;
                }
                File[] files = dir.listFiles();
                if (null == files || files.length == 0) {
                    return;
                }
                for (File file : files) {
                    BufferedReader reader = new BufferedReader(new FileReader(file));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String classFullName = line.trim();
                        if (StringUtils.isNotEmpty(classFullName)) {
                            Class<IExtensionPoint> extensionPointClass = ClassLoaderUtils.loadClass(file.getName());
                            Class<IExtensionPoint> extensionsClass = ClassLoaderUtils.loadClass(classFullName);
                            registerExtensionPoint(extensionPointClass);
                            registerExtensions(extensionsClass);
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to load extension class, extension file is  " + extensionDirUrl.getFile(), e);
            }
        }
    }

    @Override
    public void stop() {
        extensionPointClasses = Collections.emptySet();
        extensionsClasses = Collections.emptySet();
        extensionPointSpecs = Collections.emptyList();
        extensionSpecs = Collections.emptyList();
        extensionPointSpecMap = Collections.emptyMap();
        extensionSpecMap = MultiMap.emptyMap();
    }

    private static class Holder {
        private static final ExtensionManager INSTANCE = new ExtensionManager();
    }

}
