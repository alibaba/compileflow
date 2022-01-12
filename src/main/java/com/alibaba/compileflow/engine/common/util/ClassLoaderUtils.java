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
package com.alibaba.compileflow.engine.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;

/**
 * @author wuxiang
 * @author yusu
 */
public class ClassLoaderUtils {

    private static final Logger logger = LoggerFactory.getLogger(ClassLoaderUtils.class);

    public static ClassLoader getContextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    public static Class loadClass(String className) throws ClassNotFoundException {
        return loadClass(className, getContextClassLoader());
    }

    public static Class loadClass(String className, Class referrer) throws ClassNotFoundException {
        ClassLoader classLoader = getReferrerClassLoader(referrer);
        return loadClass(className, classLoader);
    }

    public static Class loadClass(String className, ClassLoader classLoader) throws ClassNotFoundException {
        if (className == null) {
            return null;
        }

        if (classLoader == null) {
            return Class.forName(className);
        } else {
            return Class.forName(className, true, classLoader);
        }
    }

    private static ClassLoader getReferrerClassLoader(Class referrer) {
        ClassLoader classLoader = null;

        if (referrer != null) {
            classLoader = referrer.getClassLoader();

            if (classLoader == null) {
                classLoader = ClassLoader.getSystemClassLoader();
            }
        }

        return classLoader;
    }

    public static URL[] getResources(String resourceName) {
        LinkedList urls = new LinkedList();
        boolean found = getResources(urls, resourceName, getContextClassLoader(), false);

        if (!found) {
            getResources(urls, resourceName, ClassLoaderUtils.class.getClassLoader(), false);
        }

        if (!found) {
            getResources(urls, resourceName, null, true);
        }

        return getDistinctURLs(urls);
    }

    public static URL[] getResources(String resourceName, Class referrer) {
        ClassLoader classLoader = getReferrerClassLoader(referrer);
        LinkedList urls = new LinkedList();

        getResources(urls, resourceName, classLoader, classLoader == null);

        return getDistinctURLs(urls);
    }

    public static URL[] getResources(String resourceName, ClassLoader classLoader) {
        LinkedList urls = new LinkedList();

        getResources(urls, resourceName, classLoader, classLoader == null);

        return getDistinctURLs(urls);
    }

    private static Enumeration getResources(String resourceName, ClassLoader classLoader, boolean sysClassLoader) {
        Enumeration resource = null;

        try {
            if (classLoader != null) {
                resource = classLoader.getResources(resourceName);
            } else if (sysClassLoader) {
                resource = ClassLoader.getSystemResources(resourceName);
            }
        } catch (IOException e) {
            logger.error("Failed to getResources error", e);
        }
        return resource;
    }

    private static boolean getResources(List urlSet, String resourceName, ClassLoader classLoader,
                                        boolean sysClassLoader) {
        if (resourceName == null) {
            return false;
        }

        Enumeration resources = getResources(resourceName, classLoader, sysClassLoader);

        if ((resources != null) && resources.hasMoreElements()) {
            while (resources.hasMoreElements()) {
                urlSet.add(resources.nextElement());
            }

            return true;
        }

        return false;
    }

    private static URL[] getDistinctURLs(LinkedList<URL> urls) {
        if ((urls == null) || (urls.size() == 0)) {
            return new URL[0];
        }

        Set<URL> urlSet = new HashSet<>(urls.size());

        for (Iterator i = urls.iterator(); i.hasNext(); ) {
            URL url = (URL)i.next();

            if (urlSet.contains(url)) {
                i.remove();
            } else {
                urlSet.add(url);
            }
        }

        return urls.toArray(new URL[urls.size()]);
    }

    public static URL getResource(String resourceName) {
        if (resourceName == null) {
            return null;
        }

        ClassLoader classLoader = getContextClassLoader();
        URL url = null;

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

    public static URL getResource(String resourceName, Class referrer) {
        if (resourceName == null) {
            return null;
        }

        ClassLoader classLoader = getReferrerClassLoader(referrer);

        return (classLoader == null)
            ? ClassLoaderUtils.class.getClassLoader().getResource(resourceName)
            : classLoader.getResource(resourceName);
    }

    public static URL getResource(String resourceName, ClassLoader classLoader) {
        if (resourceName == null) {
            return null;
        }

        return (classLoader == null)
            ? ClassLoaderUtils.class.getClassLoader().getResource(resourceName)
            : classLoader.getResource(resourceName);
    }

    public static InputStream getResourceAsStream(String resourceName) {
        URL url = getResource(resourceName);

        try {
            if (url != null) {
                return url.openStream();
            }
        } catch (IOException ignored) {
            logger.error("Failed to getResourceAsStream, resourceName is " + resourceName, ignored);
        }

        return null;
    }

    public static InputStream getResourceAsStream(String resourceName, ClassLoader classLoader) {
        URL url = getResource(resourceName, classLoader);

        try {
            if (url != null) {
                return url.openStream();
            }
        } catch (IOException e) {
            logger.error(String.format("Failed to getResourceAsStream error, resourceName %s", resourceName), e);
        }

        return null;
    }

}
