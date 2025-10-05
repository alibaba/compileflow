/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.core.classloader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages lifecycles of dynamic ClassLoaders tied.
 *
 * @author yusu
 */
public class ProcessClassLoaderManager implements ClassLoaderManager, Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessClassLoaderManager.class);

    private final Map<String, ClassLoaderInfo> managedClassLoaders = new ConcurrentHashMap<>();
    private final AtomicLong createdCount = new AtomicLong(0);
    private final AtomicLong cleanedCount = new AtomicLong(0);

    private final AtomicBoolean classLoaderMaintenanceInitialized = new AtomicBoolean(false);

    private final boolean classLoaderMaintenanceEnabled;
    private final ScheduledExecutorService scheduler;
    private final int maintenanceIntervalMinutes;
    private ScheduledFuture<?> maintenanceTask;

    public ProcessClassLoaderManager(boolean classLoaderMaintenanceEnabled, ScheduledExecutorService scheduler, int maintenanceIntervalMinutes) {
        if (maintenanceIntervalMinutes <= 0) {
            throw new IllegalArgumentException("Maintenance interval must be positive");
        }
        if (classLoaderMaintenanceEnabled && scheduler == null) {
            throw new IllegalArgumentException("Scheduler cannot be null when maintenance is enabled");
        }
        this.classLoaderMaintenanceEnabled = classLoaderMaintenanceEnabled;
        this.scheduler = scheduler;
        this.maintenanceIntervalMinutes = maintenanceIntervalMinutes;
        initClassLoaderMaintenance();
    }

    /**
     * Cleans up GC'ed weak references and reports current stats.
     * Increments cleanedCount accordingly to keep statistics consistent.
     */
    public ClassLoaderManager.ClassLoaderStats performMaintenance() {
        int before = managedClassLoaders.size();
        managedClassLoaders.entrySet().removeIf(e -> e.getValue().classLoaderRef.get() == null);
        int cleanedWeakRefs = before - managedClassLoaders.size();

        if (cleanedWeakRefs > 0) {
            LOGGER.info("ClassLoader maintenance: cleaned up {} GC'd references, {} active loaders remaining.",
                    cleanedWeakRefs, managedClassLoaders.size());
        }

        return new ClassLoaderManager.ClassLoaderStats(
                managedClassLoaders.size(),
                createdCount.get(),
                cleanedCount.get()
        );
    }

    /**
     * Returns true if the given ClassLoader is a dynamic loader we should manage.
     * Excludes system loaders; only manages recognized dynamic loaders.
     */
    private boolean isDynamicClassLoader(ClassLoader cl) {
        return cl instanceof DynamicFlowClassLoader;
    }

    /**
     * Safely closes a ClassLoader, then clears its reference.
     * Supports Closeable, URLClassLoader, and a best-effort reflective public close().
     */
    private boolean cleanupClassLoader(ClassLoaderInfo info) {
        ClassLoader classLoader = info.classLoaderRef.get();
        if (classLoader == null) {
            return false;
        }

        try {
            if (classLoader instanceof Closeable) {
                ((Closeable) classLoader).close();
                LOGGER.debug("Closed ClassLoader via Closeable interface: identifier={}", info.identifier);
                return true;
            } else if (classLoader instanceof URLClassLoader) {
                ((URLClassLoader) classLoader).close();
                LOGGER.debug("Closed ClassLoader via URLClassLoader.close(): identifier={}", info.identifier);
                return true;
            } else {
                try {
                    // Best-effort: only attempt a public close(); ignore if method not found
                    Method m = classLoader.getClass().getMethod("close");
                    m.invoke(classLoader);
                    LOGGER.debug("Closed ClassLoader via reflection: identifier={}", info.identifier);
                    return true;
                } catch (NoSuchMethodException ignored) {
                    // No public close() method; this is acceptable.
                } catch (SecurityException se) {
                    LOGGER.warn("Security manager denied reflective close for ClassLoader: identifier={}", info.identifier, se);
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("Failed to close ClassLoader: identifier={}", info.identifier, t);
        } finally {
            // Simplified WeakReference cleanup, addressing core issues only
            try {
                info.classLoaderRef.clear();
            } catch (Throwable t) {
                // Log cleanup exceptions to avoid silent failures
                LOGGER.warn("Failed to clear WeakReference for ClassLoader: identifier={}", info.identifier, t);
            }
        }
        return false;
    }

    @Override
    public void close() {
        LOGGER.info("Shutting down ProcessClassLoaderManager, cleaning up {} ClassLoaders...",
                managedClassLoaders.size());

        // Cancel scheduled maintenance task if present
        try {
            if (maintenanceTask != null) {
                maintenanceTask.cancel(false);
            }
        } catch (Throwable t) {
            LOGGER.warn("Failed to cancel ClassLoader maintenance task during shutdown", t);
        }

        int localCleaned = 0;
        for (ClassLoaderInfo info : managedClassLoaders.values()) {
            try {
                if (cleanupClassLoader(info)) {
                    localCleaned++;
                }
            } catch (Throwable t) {
                LOGGER.warn("Failed to cleanup ClassLoader during shutdown: identifier={}", info.identifier, t);
            }
        }
        managedClassLoaders.clear();
        cleanedCount.addAndGet(localCleaned);

        ClassLoaderManager.ClassLoaderStats finalStats =
                new ClassLoaderManager.ClassLoaderStats(0, createdCount.get(), cleanedCount.get());
        LOGGER.info("ProcessClassLoaderManager shutdown complete. Final stats: {}", finalStats);
    }

    public synchronized void initClassLoaderMaintenance() {
        if (this.classLoaderMaintenanceEnabled && classLoaderMaintenanceInitialized.compareAndSet(false, true)) {
            maintenanceTask = scheduler.scheduleWithFixedDelay(
                    () -> {
                        try {
                            ClassLoaderStats stats = performMaintenance();
                            LOGGER.debug("ClassLoader maintenance run completed. Stats: {}", stats);
                        } catch (Exception e) {
                            LOGGER.warn("ClassLoader maintenance task failed", e);
                        }
                    },
                    maintenanceIntervalMinutes,
                    maintenanceIntervalMinutes,
                    TimeUnit.MINUTES
            );

            LOGGER.info("ClassLoader maintenance scheduled every {} minutes.", maintenanceIntervalMinutes);
        } else if (!this.classLoaderMaintenanceEnabled) {
            LOGGER.info("ClassLoader maintenance is disabled. Cleanup will only occur opportunistically.");
        }
    }

    @Override
    public void registerClassLoader(String identifier, ClassLoader classLoader, String digest) {
        // Manage any dynamic ClassLoader; decoupled from maintenance switch
        if (isDynamicClassLoader(classLoader)) {
            opportunisticSweep();

            ClassLoaderInfo info = new ClassLoaderInfo(identifier,
                    new WeakReference<>(classLoader), System.currentTimeMillis(), digest);

            ClassLoaderInfo oldInfo = managedClassLoaders.put(identifier, info);
            if (oldInfo != null) {
                LOGGER.debug("Replaced existing ClassLoader registration: identifier={}, oldDigest={}, newDigest={}",
                        identifier, oldInfo.digest, digest);
                if (cleanupClassLoader(oldInfo)) {
                    cleanedCount.incrementAndGet();
                }
            } else {
                createdCount.incrementAndGet();
            }

            LOGGER.debug("Registered new ClassLoader: identifier={}, type={}, digest={}",
                    identifier, classLoader.getClass().getSimpleName(), digest);
        }
    }

    @Override
    public void unregisterClassLoader(String identifier) {
        ClassLoaderInfo info = managedClassLoaders.remove(identifier);
        if (info != null) {
            if (cleanupClassLoader(info)) {
                cleanedCount.incrementAndGet();
            }
            LOGGER.debug("Unregistered ClassLoader: identifier={}", identifier);
        }
        opportunisticSweep();
    }

    @Override
    public ClassLoaderManager.ClassLoaderStats getStats() {
        return new ClassLoaderManager.ClassLoaderStats(
                managedClassLoaders.size(),
                createdCount.get(),
                cleanedCount.get()
        );
    }

    /**
     * Opportunistic cleanup: remove ClassLoader entries that have been GC'd; add exception handling and stats update
     */
    private void opportunisticSweep() {
        try {
            int before = managedClassLoaders.size();
            managedClassLoaders.entrySet().removeIf(e -> e.getValue().classLoaderRef.get() == null);
            int cleanedInSweep = before - managedClassLoaders.size();

            if (cleanedInSweep > 0) {
                LOGGER.debug("Opportunistic sweep cleaned up {} GC'd references, {} active loaders remaining.",
                        cleanedInSweep, managedClassLoaders.size());
            }
        } catch (Exception e) {
            LOGGER.warn("Opportunistic ClassLoader sweep failed", e);
        }
    }

    private static class ClassLoaderInfo {
        final String identifier;
        final WeakReference<ClassLoader> classLoaderRef;
        final long creationTime;
        final String digest; // Optional digest for version tracking

        ClassLoaderInfo(String identifier, WeakReference<ClassLoader> classLoaderRef, long creationTime, String digest) {
            this.identifier = identifier;
            this.classLoaderRef = classLoaderRef;
            this.creationTime = creationTime;
            this.digest = digest;
        }
    }

}
