package com.alibaba.compileflow.engine.core.version;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Version metadata registry: active/ready version maps and extension metadata per code.
 * Infrastructure only; routing strategy is up to business code.
 *
 * @author yusu
 */
public final class VersionMetadataManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(VersionMetadataManager.class);

    private static final VersionMetadataManager INSTANCE = new VersionMetadataManager();

    private final Map<String, String> activeVersionByCode = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> readyVersionsByCode = new ConcurrentHashMap<>();

    private final Map<String, Object> extensionMetadataByCode = new ConcurrentHashMap<>();

    private VersionMetadataManager() {
    }

    public static VersionMetadataManager getInstance() {
        return INSTANCE;
    }

    public void updateActiveVersion(String code, String version) {
        if (code == null) {
            return;
        }
        if (version == null) {
            activeVersionByCode.remove(code);
        } else {
            activeVersionByCode.put(code, version);
        }
        LOGGER.debug("activeVersion updated: code={}, version={}", code, version);
    }

    public String getActiveVersion(String code) {
        return activeVersionByCode.get(code);
    }

    public void markReady(String code, String version) {
        if (code == null || version == null) {
            return;
        }
        readyVersionsByCode.computeIfAbsent(code, k -> ConcurrentHashMap.newKeySet()).add(version);
        LOGGER.debug("version ready: {}#{}", code, version);
    }

    public void markNotReady(String code, String version) {
        if (code == null || version == null) {
            return;
        }
        Set<String> set = readyVersionsByCode.get(code);
        if (set != null) {
            set.remove(version);
        }
        LOGGER.debug("version not-ready: {}#{}", code, version);
    }

    public boolean isReady(String code, String version) {
        Set<String> set = readyVersionsByCode.get(code);
        return set != null && set.contains(version);
    }

    public Set<String> getReadyVersions(String code) {
        Set<String> set = readyVersionsByCode.get(code);
        return set == null ? Collections.emptySet() : Collections.unmodifiableSet(set);
    }

    // =============================================================================================
    // Extension metadata management (supports custom routing strategies)
    // =============================================================================================

    /**
     * Store user-defined routing metadata.
     *
     * @param code     process code
     * @param metadata user-defined metadata object
     * @param <T>      metadata type
     */
    public <T> void putExtensionMetadata(String code, T metadata) {
        if (code == null) {
            return;
        }
        if (metadata == null) {
            extensionMetadataByCode.remove(code);
            LOGGER.debug("Extension metadata removed: code={}", code);
        } else {
            extensionMetadataByCode.put(code, metadata);
            LOGGER.debug("Extension metadata updated: code={}, type={}", code, metadata.getClass().getSimpleName());
        }
    }

    /**
     * Get user-defined routing metadata.
     *
     * @param code         process code
     * @param expectedType expected metadata type
     * @param <T>          metadata type
     * @return metadata object, or null if not exists or type mismatch
     */
    @SuppressWarnings("unchecked")
    public <T> T getExtensionMetadata(String code, Class<T> expectedType) {
        Object metadata = extensionMetadataByCode.get(code);
        if (metadata != null && expectedType.isInstance(metadata)) {
            return (T) metadata;
        }
        return null;
    }

    /**
     * Get raw extension metadata object.
     *
     * @param code process code
     * @return metadata object of any type
     */
    public Object getExtensionMetadata(String code) {
        return extensionMetadataByCode.get(code);
    }

    /**
     * Check whether extension metadata exists.
     */
    public boolean hasExtensionMetadata(String code) {
        return extensionMetadataByCode.containsKey(code);
    }

    /**
     * Get all process codes that have extension metadata.
     */
    public Set<String> getExtensionMetadataCodes() {
        return Collections.unmodifiableSet(extensionMetadataByCode.keySet());
    }

    /**
     * Clear all version information (primarily for tests).
     */
    public void clearAll() {
        activeVersionByCode.clear();
        readyVersionsByCode.clear();
        extensionMetadataByCode.clear();
        LOGGER.warn("All version metadata cleared");
    }
}


