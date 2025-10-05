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
package com.alibaba.compileflow.engine.core.infrastructure.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;

/**
 * A utility class for handling {@link ClassLoader} and resource loading operations.
 * <p>
 * This class provides a consistent and robust way to load classes and resources,
 * gracefully handling different classloader hierarchies (e.g., context classloader,
 * system classloader) to ensure reliability in various deployment environments.
 *
 * @author wuxiang
 * @author yusu
 */
public class ClassLoaderUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClassLoaderUtils.class);

    /**
     * Gets the current thread's context classloader.
     *
     * @return The context classloader.
     */
    public static ClassLoader getContextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    /**
     * Loads a class using the current thread's context classloader.
     *
     * @param className The fully qualified name of the class to load.
     * @return The loaded {@link Class} object.
     * @throws ClassNotFoundException if the class cannot be found.
     */
    public static Class loadClass(String className) throws ClassNotFoundException {
        return loadClass(className, getContextClassLoader());
    }

    /**
     * Loads a class using the classloader of a referrer class.
     *
     * @param className The fully qualified name of the class to load.
     * @param referrer  A class whose classloader should be used.
     * @return The loaded {@link Class} object.
     * @throws ClassNotFoundException if the class cannot be found.
     */
    public static Class loadClass(String className, Class<?> referrer) throws ClassNotFoundException {
        ClassLoader classLoader = getReferrerClassLoader(referrer);
        return loadClass(className, classLoader);
    }

    /**
     * Loads a class using the specified classloader. If the provided classloader is null,
     * it falls back to {@code Class.forName(className)}.
     *
     * @param className   The fully qualified name of the class to load.
     * @param classLoader The classloader to use.
     * @return The loaded {@link Class} object.
     * @throws ClassNotFoundException if the class cannot be found.
     */
    public static Class<?> loadClass(String className, ClassLoader classLoader) throws ClassNotFoundException {
        if (className == null) {
            return null;
        }

        if (classLoader == null) {
            return Class.forName(className);
        } else {
            return Class.forName(className, true, classLoader);
        }
    }

    /**
     * Gets the classloader from a referrer class, falling back to the system classloader
     * if the referrer's classloader is null (e.g., for bootstrap classes).
     */
    private static ClassLoader getReferrerClassLoader(Class<?> referrer) {
        ClassLoader classLoader = null;

        if (referrer != null) {
            classLoader = referrer.getClassLoader();

            if (classLoader == null) {
                classLoader = ClassLoader.getSystemClassLoader();
            }
        }

        return classLoader;
    }

    /**
     * Finds all resources with the given name, searching through a standard hierarchy
     * of classloaders (context, this class's, and system).
     *
     * @param resourceName The name of the resource to find.
     * @return An array of {@link URL} objects for all found resources.
     */
    public static URL[] getResources(String resourceName) {
        LinkedList<URL> urls = new LinkedList<>();
        boolean found = getResources(urls, resourceName, getContextClassLoader(), false);

        if (!found) {
            getResources(urls, resourceName, ClassLoaderUtils.class.getClassLoader(), false);
        }

        if (!found) {
            getResources(urls, resourceName, null, true);
        }

        return getDistinctURLs(urls);
    }

    /**
     * Finds all resources with the given name using a referrer's classloader.
     *
     * @param resourceName The name of the resource to find.
     * @param referrer     A class whose classloader should be used for the search.
     * @return An array of {@link URL} objects for all found resources.
     */
    public static URL[] getResources(String resourceName, Class<?> referrer) {
        ClassLoader classLoader = getReferrerClassLoader(referrer);
        LinkedList<URL> urls = new LinkedList<>();

        getResources(urls, resourceName, classLoader, classLoader == null);

        return getDistinctURLs(urls);
    }

    /**
     * Finds all resources with the given name using a specific classloader.
     *
     * @param resourceName The name of the resource to find.
     * @param classLoader  The classloader to use for the search.
     * @return An array of {@link URL} objects for all found resources.
     */
    public static URL[] getResources(String resourceName, ClassLoader classLoader) {
        List<URL> urls = new LinkedList<>();

        getResources(urls, resourceName, classLoader, classLoader == null);

        return getDistinctURLs(urls);
    }

    private static Enumeration<URL> getResources(String resourceName, ClassLoader classLoader, boolean sysClassLoader) {
        Enumeration<URL> resource = null;

        try {
            if (classLoader != null) {
                resource = classLoader.getResources(resourceName);
            } else if (sysClassLoader) {
                resource = ClassLoader.getSystemResources(resourceName);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to getResources: errorType={}, message={}",
                    e.getClass().getSimpleName(), e.getMessage(), e);
        }
        return resource;
    }

    private static boolean getResources(List<URL> urlSet, String resourceName, ClassLoader classLoader,
                                        boolean sysClassLoader) {
        if (resourceName == null) {
            return false;
        }

        Enumeration<URL> resources = getResources(resourceName, classLoader, sysClassLoader);

        if ((resources != null) && resources.hasMoreElements()) {
            while (resources.hasMoreElements()) {
                urlSet.add(resources.nextElement());
            }

            return true;
        }

        return false;
    }

    /**
     * Removes duplicate URLs from a list.
     */
    private static URL[] getDistinctURLs(List<URL> urls) {
        if ((urls == null) || (urls.size() == 0)) {
            return new URL[0];
        }

        Set<URL> urlSet = new HashSet<>(urls.size());

        for (Iterator<URL> i = urls.iterator(); i.hasNext(); ) {
            URL url = i.next();

            if (urlSet.contains(url)) {
                i.remove();
            } else {
                urlSet.add(url);
            }
        }

        return urls.toArray(new URL[0]);
    }

    /**
     * Finds the first resource with the given name using a standard search hierarchy.
     *
     * @param resourceName The name of the resource to find.
     * @return A {@link URL} object, or null if the resource is not found.
     */
    public static URL getResource(String resourceName) {
        if (resourceName == null) {
            return null;
        }

        ClassLoader classLoader = getContextClassLoader();
        URL url;

        if (classLoader != null) {
            url = classLoader.getResource(resourceName);

            if (url != null) {
                return url;
            }
        }

        classLoader = ClassLoaderUtils.class.getClassLoader();

        if (classLoader != null) {
            url = classLoader.getResource(resourceName);

            if (url != null) {
                return url;
            }
        }

        return ClassLoader.getSystemResource(resourceName);
    }

    /**
     * Finds the first resource with the given name using a referrer's classloader.
     *
     * @param resourceName The name of the resource to find.
     * @param referrer     A class whose classloader should be used.
     * @return A {@link URL} object, or null if the resource is not found.
     */
    public static URL getResource(String resourceName, Class<?> referrer) {
        if (resourceName == null) {
            return null;
        }

        ClassLoader classLoader = getReferrerClassLoader(referrer);

        return (classLoader == null)
                ? ClassLoaderUtils.class.getClassLoader().getResource(resourceName)
                : classLoader.getResource(resourceName);
    }

    /**
     * Finds the first resource with the given name using a specific classloader.
     *
     * @param resourceName The name of the resource to find.
     * @param classLoader  The classloader to use.
     * @return A {@link URL} object, or null if the resource is not found.
     */
    public static URL getResource(String resourceName, ClassLoader classLoader) {
        if (resourceName == null) {
            return null;
        }

        return (classLoader == null)
                ? ClassLoaderUtils.class.getClassLoader().getResource(resourceName)
                : classLoader.getResource(resourceName);
    }

    /**
     * Opens an {@link InputStream} for a resource found via the standard search hierarchy.
     *
     * @param resourceName The name of the resource.
     * @return An {@link InputStream}, or null if the resource is not found.
     */
    public static InputStream getResourceAsStream(String resourceName) {
        URL url = getResource(resourceName);

        try {
            if (url != null) {
                return url.openStream();
            }
        } catch (IOException e) {
            LOGGER.error("Failed to getResourceAsStream, resourceName is {}", resourceName, e);
        }

        return null;
    }

    /**
     * Opens an {@link InputStream} for a resource found via a specific classloader.
     *
     * @param resourceName The name of the resource.
     * @param classLoader  The classloader to use.
     * @return An {@link InputStream}, or null if the resource is not found.
     */
    public static InputStream getResourceAsStream(String resourceName, ClassLoader classLoader) {
        URL url = getResource(resourceName, classLoader);

        try {
            if (url != null) {
                return url.openStream();
            }
        } catch (IOException e) {
            LOGGER.error("Failed to getResourceAsStream: resourceName={}, errorType={}, message={}",
                    resourceName, e.getClass().getSimpleName(), e.getMessage(), e);
        }

        return null;
    }

}
