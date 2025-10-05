package com.alibaba.compileflow.engine.core.classloader;

/**
 * Unified interface for managing ClassLoader lifecycle across CompileFlow components.
 *
 * <p>This interface provides a consistent way to manage ClassLoader resources,
 * preventing memory leaks and enabling unified monitoring and cleanup.</p>
 *
 * @author yusu
 */
public interface ClassLoaderLifecycleManager {

    /**
     * Registers a ClassLoader for lifecycle management.
     *
     * @param identifier  unique identifier for the ClassLoader
     * @param classLoader the ClassLoader to manage
     * @param digest      content digest for version tracking
     */
    void registerClassLoader(String identifier, ClassLoader classLoader, String digest);

    /**
     * Unregisters a ClassLoader from lifecycle management.
     *
     * @param identifier the identifier of the ClassLoader to unregister
     */
    void unregisterClassLoader(String identifier);

    /**
     * Gets statistics about managed ClassLoaders.
     *
     * @return statistics about ClassLoader usage
     */
    ClassLoaderStats getStats();

    // Cleanup is performed via Closeable.close() on implementations.

    /**
     * Statistics about ClassLoader usage.
     */
    class ClassLoaderStats {
        private final int currentCount;
        private final long totalCreated;
        private final long totalCleaned;

        public ClassLoaderStats(int currentCount, long totalCreated, long totalCleaned) {
            this.currentCount = currentCount;
            this.totalCreated = totalCreated;
            this.totalCleaned = totalCleaned;
        }

        public int getCurrentCount() {
            return currentCount;
        }

        public long getTotalCreated() {
            return totalCreated;
        }

        public long getTotalCleaned() {
            return totalCleaned;
        }

        @Override
        public String toString() {
            return String.format("ClassLoaderStats{current=%d, created=%d, cleaned=%d}",
                    currentCount, totalCreated, totalCleaned);
        }
    }
}
