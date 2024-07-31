package com.alibaba.compileflow.engine.common.extension;

import com.alibaba.compileflow.engine.common.Lifecycle;
import com.alibaba.compileflow.engine.common.MultiMap;
import com.alibaba.compileflow.engine.common.extension.annotation.ExtensionRealization;
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
 * Manages extensions and extension points, implementing the {@link Lifecycle} interface.
 * It loads, sorts, and retrieves extensions based on their priorities.
 *
 * @author yusu
 */
public class ExtensionManager implements Lifecycle {

    private static final String EXTENSION_DIRECTORY = "META-INF/extensions/";

    private static final Logger logger = LoggerFactory.getLogger(ExtensionManager.class);

    private Set<Class<? extends Extension>> extensionPointClasses = new HashSet<>();
    private Set<Class<? extends Extension>> extensionsClasses = new HashSet<>();

    private List<ExtensionPointSpec> extensionPointSpecs = new ArrayList<>();
    private Map<String, ExtensionPointSpec> extensionPointSpecMap = new HashMap<>();
    private List<ExtensionSpec> extensionSpecs = new ArrayList<>();
    private MultiMap<String, ExtensionSpec> extensionSpecMap = new MultiMap<>();

    public static ExtensionManager getInstance() {
        return Holder.INSTANCE;
    }

    @Override
    public void init() {
        loadExtensions();
        sortExtensionsByPriority();
    }

    public ExtensionPointSpec getExtensionPoint(String extensionCode) {
        return extensionPointSpecMap.get(extensionCode);
    }

    public List<ExtensionSpec> getExtensions(String extensionCode) {
        return extensionSpecMap.get(extensionCode);
    }

    public <T extends Extension> List<T> getExtensions(String extensionCode, Class<T> extensionClass) {
        List<ExtensionSpec> extensionSpecs = extensionSpecMap.get(extensionCode);
        if (CollectionUtils.isEmpty(extensionSpecs)) {
            return Collections.emptyList();
        }
        return extensionSpecs.stream().map(ExtensionSpec::getExtension)
            .filter(extension -> extensionClass.isAssignableFrom(extension.getClass()))
            .map(extension -> (T) extension).collect(Collectors.toList());
    }

    public void registerExtensionPoint(Class<? extends Extension> extensionPointClass) {
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
            if (Extension.class.isAssignableFrom(returnType)) {
                registerExtensionPoint((Class<? extends Extension>) returnType);
            }
        }
    }

    public void registerExtension(Class<? extends Extension> extensionClass) {
        Extension extension = null;
        try {
            extension = ClassUtils.newInstance(extensionClass);
        } catch (Exception e) {
            logger.error("Failed to register extensions, " + e.getMessage(), e);
        }
        if (extension != null) {
            registerExtension(extension);
        }
    }

    public void sortExtensionsByPriority() {
        if (extensionSpecMap != null) {
            extensionSpecMap.values().forEach(this::sortExtensionMethod);
        }
    }

    private void registerExtension(Extension extension) {
        Class<? extends Extension> extensionClass = extension.getClass();
        if (extensionsClasses.contains(extensionClass)) {
            return;
        }

        ExtensionRealization extensionRealizationAnnotation = extensionClass.getAnnotation(ExtensionRealization.class);
        if (extensionRealizationAnnotation == null) {
            return;
        }

        ExtensionPoint extensionPointAnnotation = AnnotationUtils.findAnnotation(extensionClass, ExtensionPoint.class);
        if (extensionPointAnnotation != null) {
            ExtensionSpec extensionSpec = ClassExtensionSpec.of(extensionRealizationAnnotation,
                extensionPointAnnotation, extension);
            extensionSpecs.add(extensionSpec);
            extensionSpecMap.put(extensionSpec.getCode(), extensionSpec);
        }

        Method[] methods = extensionClass.getMethods();
        for (Method method : methods) {
            extensionPointAnnotation = AnnotationUtils.findAnnotation(method, ExtensionPoint.class);
            if (extensionPointAnnotation != null) {
                ExtensionSpec extensionSpec = MethodExtensionSpec.of(extensionRealizationAnnotation,
                    extensionPointAnnotation, extension, method);
                extensionSpecs.add(extensionSpec);
                extensionSpecMap.put(extensionSpec.getCode(), extensionSpec);
            }
            Class<?> returnType = method.getReturnType();
            if (Extension.class.isAssignableFrom(returnType)) {
                Extension subExtension = null;
                try {
                    subExtension = MethodUtils.invoke(extension, method);
                } catch (Exception e) {
                    logger.error("Failed to get subExtension, method is " + method.getName(), e);
                }
                if (subExtension != null) {
                    registerExtension(subExtension);
                }
            }
        }
    }

    private void sortExtensionMethod(List<ExtensionSpec> extensionSpecs) {
        extensionSpecs.sort(Comparator.comparing(ExtensionSpec::getPriority).reversed());
    }

    private void loadExtensions() {
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
                    Class<Extension> extensionPointClass = ClassLoaderUtils.loadClass(file.getName());
                    registerExtensionPoint(extensionPointClass);
                    BufferedReader reader = new BufferedReader(new FileReader(file));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String classFullName = line.trim();
                        if (StringUtils.isNotBlank(classFullName)) {
                            Class<Extension> extensionClass = ClassLoaderUtils.loadClass(classFullName);
                            registerExtension(extensionClass);
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
