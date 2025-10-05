package com.alibaba.compileflow.engine.core.extension;

import com.alibaba.compileflow.engine.core.infrastructure.utils.ClassLoaderUtils;
import com.alibaba.compileflow.engine.core.infrastructure.utils.ClassUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Central registry for extension points and implementations.
 * Supports SPI files in {@code META-INF/extensions/} and annotation scanning.
 *
 * @author yusu
 */
public class ExtensionManager {

    private static final String EXTENSION_DIRECTORY = "META-INF/extensions/";

    private static final Logger LOGGER = LoggerFactory.getLogger(ExtensionManager.class);

    private boolean autoScanEnabled = true;
    private String[] scanPackages = getDefaultScanPackages();

    private Set<Class<? extends Extension>> extensionClasses = new HashSet<>();

    private List<ExtensionPointSpec> extensionPointSpecs = new ArrayList<>();
    private Map<String, ExtensionPointSpec> extensionPointSpecMap = new HashMap<>();
    private List<ExtensionRealizationSpec> extensionRealizationSpecs = new ArrayList<>();
    private Map<String, List<ExtensionRealizationSpec>> extensionRealizationSpecMap = new HashMap<>();

    public static ExtensionManager getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * Initialize from SPI and optional annotation scan; call once at startup.
     */
    public void init() {
        LOGGER.info("Initializing ExtensionManager with SPI and auto-scan support");

        loadExtensionsFromSPI();

        if (autoScanEnabled) {
            autoScanExtensions();
        }

        sortExtensionsByPriority();

        LOGGER.info("ExtensionManager initialized. Total extension points: {}, implementations: {}",
                extensionPointSpecMap.size(), extensionRealizationSpecs.size());
    }

    /**
     * Get the spec for an extension point code (or null).
     */
    public ExtensionPointSpec getExtensionPoint(String extensionCode) {
        return extensionPointSpecMap.get(extensionCode);
    }

    /**
     * Get implementations for an extension point (sorted by priority, desc).
     */
    public List<ExtensionRealizationSpec> getExtensions(String extensionCode) {
        return extensionRealizationSpecMap.get(extensionCode);
    }

    /**
     * Get extension instances for a point, filtered by type.
     */
    public <T extends Extension> List<T> getExtensions(String extensionCode, Class<T> extensionClass) {
        List<ExtensionRealizationSpec> extensionSpecs = extensionRealizationSpecMap.get(extensionCode);
        if (CollectionUtils.isEmpty(extensionSpecs)) {
            return Collections.emptyList();
        }
        return extensionSpecs.stream().map(ExtensionRealizationSpec::getTarget)
                .filter(extension -> extensionClass.isAssignableFrom(extension.getClass()))
                .map(extension -> (T) extension).collect(Collectors.toList());
    }

    /**
     * Register an extension point by inspecting @ExtensionPoint methods (recursive for nested types).
     */
    public void registerExtensionPoint(Class<? extends Extension> extensionClass) {
        if (extensionClasses.contains(extensionClass)) {
            return;
        }

        Method[] methods = extensionClass.getMethods();
        for (Method method : methods) {
            ExtensionPoint extensionPointAnnotation = AnnotationUtils.findAnnotation(method, ExtensionPoint.class);
            if (extensionPointAnnotation != null) {
                ExtensionPointSpec extensionPointSpec = ExtensionPointSpec.of(extensionPointAnnotation);
                extensionPointSpecs.add(extensionPointSpec);
                extensionPointSpecMap.put(extensionPointSpec.getCode(), extensionPointSpec);
            }
            Class<?> returnType = method.getReturnType();
            if (Extension.class.isAssignableFrom(returnType)) {
                registerExtensionPoint((Class<? extends Extension>) returnType);
            }
        }
    }

    /**
     * Register an extension implementation by instantiating the class.
     */
    public void registerExtension(Class<? extends Extension> extensionClass) {
        Extension extension = null;
        try {
            extension = ClassUtils.newInstance(extensionClass);
        } catch (Exception e) {
            LOGGER.error("Failed to register extensions: extensionClass={}, errorType={}, message={}",
                    extensionClass.getName(), e.getClass().getSimpleName(), e.getMessage(), e);
        }
        if (extension != null) {
            registerExtension(extension);
        }
    }

    /**
     * Register multiple pre-instantiated extension objects.
     */
    public void registerExtensions(Set<Extension> extensions) {
        if (CollectionUtils.isEmpty(extensions)) {
            return;
        }
        for (Extension extension : extensions) {
            registerExtension(extension);
        }
    }

    /**
     * Sort implementations by declared priority (desc).
     */
    public void sortExtensionsByPriority() {
        extensionRealizationSpecMap.values().forEach(this::sortExtensionRealizaion);
    }

    private void registerExtension(Extension extension) {
        Class<? extends Extension> extensionClass = extension.getClass();
        if (extensionClasses.contains(extensionClass)) {
            return;
        }

        ExtensionRealization extensionAnnotation = extensionClass.getAnnotation(ExtensionRealization.class);
        if (extensionAnnotation == null) {
            return;
        }

        Method[] methods = extensionClass.getMethods();
        for (Method method : methods) {
            ExtensionPoint extensionPointAnnotation = AnnotationUtils.findAnnotation(method, ExtensionPoint.class);
            if (extensionPointAnnotation != null) {
                ExtensionRealizationSpec extensionRealizationSpec = ExtensionRealizationSpec.of(extensionAnnotation,
                        extension);
                extensionRealizationSpecs.add(extensionRealizationSpec);
                extensionRealizationSpecMap
                        .computeIfAbsent(extensionPointAnnotation.code(), k -> new ArrayList<>())
                        .add(extensionRealizationSpec);
            }
            Class<?> returnType = method.getReturnType();
            if (Extension.class.isAssignableFrom(returnType)) {
                Extension subExtension = null;
                try {
                    subExtension = (Extension) method.invoke(extension);
                } catch (Exception e) {
                    LOGGER.error("Failed to get subExtension: method={}, errorType={}, message={}",
                            method.getName(), e.getClass().getSimpleName(), e.getMessage(), e);
                }
                if (subExtension != null) {
                    registerExtension(subExtension);
                }
            }
        }
    }

    private void sortExtensionRealizaion(List<ExtensionRealizationSpec> extensionSpecs) {
        extensionSpecs.sort(Comparator.comparing(ExtensionRealizationSpec::getPriority).reversed());
    }

    /**
     * Loads extensions from SPI files (renamed original method, maintains existing logic).
     */
    private void loadExtensionsFromSPI() {
        LOGGER.debug("Loading extensions from SPI files in {}", EXTENSION_DIRECTORY);

        URL[] extensionDirUrls = ClassLoaderUtils.getResources(EXTENSION_DIRECTORY);
        if (ArrayUtils.isEmpty(extensionDirUrls)) {
            LOGGER.debug("No SPI extension directories found");
            return;
        }

        int spiExtensionCount = 0;
        for (URL extensionDirUrl : extensionDirUrls) {
            try {
                String filePath = URLDecoder.decode(extensionDirUrl.getFile(), "UTF-8");
                File dir = new File(filePath);
                if (!dir.exists() || !dir.isDirectory()) {
                    continue;
                }
                File[] files = dir.listFiles();
                if (null == files || files.length == 0) {
                    continue;
                }
                for (File file : files) {
                    Class<Extension> extensionClass = ClassLoaderUtils.loadClass(file.getName());
                    registerExtensionPoint(extensionClass);
                    BufferedReader reader = new BufferedReader(new FileReader(file));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String classFullName = line.trim();
                        if (StringUtils.isNotBlank(classFullName)) {
                            Class<Extension> extensionRealizationClass = ClassLoaderUtils.loadClass(classFullName);
                            registerExtension(extensionRealizationClass);
                            spiExtensionCount++;
                        }
                    }
                    reader.close();
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load SPI extension: path={}, errorType={}, message={}",
                        extensionDirUrl.getFile(), e.getClass().getSimpleName(), e.getMessage(), e);
            }
        }

        if (spiExtensionCount > 0) {
            LOGGER.info("Loaded {} extensions from SPI files", spiExtensionCount);
        }
    }

    /**
     * Auto-scans annotation configured extensions (minimal implementation).
     * Directly scans based on @ExtensionRealization annotation for simplicity and efficiency.
     */
    private void autoScanExtensions() {
        LOGGER.debug("Auto-scanning extensions based on @ExtensionRealization annotation");

        try {
            Set<Class<?>> implementationClasses = scanExtensionImplementationsByAnnotation();

            int registeredCount = 0;
            for (Class<?> clazz : implementationClasses) {
                registerExtensionImplementationClass(clazz);
                registeredCount++;

                registerExtensionPointsFromImplementation(clazz);
            }

            if (registeredCount > 0) {
                LOGGER.info("Auto-scanned and registered {} extension implementations", registeredCount);
            }

        } catch (Exception e) {
            LOGGER.error("Failed to auto-scan extensions: errorType={}, message={}",
                    e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    /**
     * Scans for extension implementation classes based on annotations (minimal and efficient).
     * Spring scanner filters by annotations for good performance, no complex package conventions needed.
     */
    private Set<Class<?>> scanExtensionImplementationsByAnnotation() {
        Set<Class<?>> implementations = new HashSet<>();

        ClassPathScanningCandidateComponentProvider scanner = createComponentScanner();
        // Core: only return classes with @ExtensionRealization annotation
        scanner.addIncludeFilter(new AnnotationTypeFilter(ExtensionRealization.class));

        for (String basePackage : scanPackages) {
            try {
                Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);
                for (BeanDefinition candidate : candidates) {
                    try {
                        Class<?> clazz = ClassLoaderUtils.loadClass(candidate.getBeanClassName());
                        if (Extension.class.isAssignableFrom(clazz) && !clazz.isInterface()) {
                            implementations.add(clazz);
                            LOGGER.debug("Found extension implementation: {}", clazz.getName());
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Failed to load extension implementation: className={}, errorType={}, message={}",
                                candidate.getBeanClassName(), e.getClass().getSimpleName(), e.getMessage(), e);
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("Package {} not accessible, skipping", basePackage);
            }
        }

        return implementations;
    }

    /**
     * Automatically derives and registers extension point interfaces from implementation classes.
     * Avoids duplicate scanning by directly deriving interfaces from implementation classes.
     */
    private void registerExtensionPointsFromImplementation(Class<?> implementationClass) {
        Class<?>[] interfaces = implementationClass.getInterfaces();

        for (Class<?> interfaceClass : interfaces) {
            if (Extension.class.isAssignableFrom(interfaceClass) && hasExtensionPointMethod(interfaceClass)) {
                try {
                    registerExtensionPointClass(interfaceClass);
                    LOGGER.debug("Auto-registered extension point from implementation {}: {}",
                            implementationClass.getName(), interfaceClass.getName());
                } catch (Exception e) {
                    LOGGER.debug("Failed to register extension point from implementation", e);
                }
            }
        }
    }

    /**
     * Checks if an interface has methods annotated with @ExtensionPoint.
     */
    private boolean hasExtensionPointMethod(Class<?> clazz) {
        Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            if (AnnotationUtils.findAnnotation(method, ExtensionPoint.class) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates a classpath scanner for component discovery.
     */
    private ClassPathScanningCandidateComponentProvider createComponentScanner() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.setResourcePattern("**/*.class");
        return scanner;
    }

    /**
     * Registers a scanned extension point class.
     */
    @SuppressWarnings("unchecked")
    private void registerExtensionPointClass(Class<?> clazz) {
        try {
            registerExtensionPoint((Class<? extends Extension>) clazz);
        } catch (Exception e) {
            LOGGER.error("Failed to register scanned extension point: className={}, errorType={}, message={}",
                    clazz.getName(), e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    /**
     * Registers a scanned extension implementation class.
     */
    @SuppressWarnings("unchecked")
    private void registerExtensionImplementationClass(Class<?> clazz) {
        try {
            registerExtension((Class<? extends Extension>) clazz);
        } catch (Exception e) {
            LOGGER.error("Failed to register scanned extension implementation: className={}, errorType={}, message={}",
                    clazz.getName(), e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    /**
     * Gets default scan package paths.
     * Minimal approach: directly scan common root packages, filter by annotations.
     */
    private String[] getDefaultScanPackages() {
        return new String[]{"com.alibaba.compileflow, com.compileflow.extension"};
    }

    /**
     * Sets auto-scan configuration (optional, for custom configuration).
     */
    public void setAutoScanEnabled(boolean autoScanEnabled) {
        this.autoScanEnabled = autoScanEnabled;
    }

    private static class Holder {
        private static final ExtensionManager INSTANCE = new ExtensionManager();
    }

}
