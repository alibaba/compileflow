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

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * @author yusu
 */
public class PackageUtils {

    private static final Logger logger = LoggerFactory.getLogger(PackageUtils.class);

    private static final boolean RECURSIVE = true;

    private static final String FILE = "file";

    private static final String JAR = "jar";

    private static final String CLASS_SUFFIX = ".class";

    /**
     * @param packageName
     * @return
     */
    public static Set<Class<?>> getAllClassInPacakge(String packageName) {
        Set<Class<?>> classes = new HashSet<>();
        if (StringUtils.isEmpty(packageName)) {
            return classes;
        }
        String packagePath = packageName.replace('.', '/');

        URL[] urls = ClassLoaderUtils.getResources(packagePath);
        if (ArrayUtils.isEmpty(urls)) {
            return classes;
        }

        for (URL url : urls) {
            String protocol = url.getProtocol();
            if (FILE.equals(protocol)) {
                String filePath = null;
                try {
                    filePath = URLDecoder.decode(url.getFile(), "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    logger.error("Failed to decode, file is " + url.getFile(), e);
                }
                if (filePath == null) {
                    continue;
                }
                scanClassInDir(classes, packageName, filePath, RECURSIVE);
            } else if (JAR.equals(protocol)) {
                scanClassInJar(classes, packageName, url);
            }
        }

        return classes;
    }

    /**
     * scan all classes in directory
     *
     * @param classes
     * @param packageName
     * @param filePath
     * @param recursive
     */
    private static void scanClassInDir(Set<Class<?>> classes, String packageName, String filePath,
                                       boolean recursive) {
        File dir = new File(filePath);
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles(file -> (recursive && file.isDirectory())
            || (file.getName().endsWith(CLASS_SUFFIX)));
        if (null == files || files.length == 0) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                scanClassInDir(classes, packageName + "." + file.getName(),
                    file.getAbsolutePath(), recursive);
            } else {
                String className = file.getName().substring(0,
                    file.getName().length() - CLASS_SUFFIX.length());
                String classFullName = packageName + '.' + className;

                try {
                    Class clazz = ClassLoaderUtils.loadClass(classFullName);
                    classes.add(clazz);
                } catch (ClassNotFoundException e) {
                    logger.error("Class not found, class name is  " + classFullName, e);
                }
            }
        }
    }

    /**
     * scan all classes in jar
     *
     * @param classes
     * @param packageName
     * @param url
     */
    private static void scanClassInJar(Set<Class<?>> classes, String packageName, URL url) {
        String package2Path = packageName.replace('.', '/');

        JarFile jar;
        try {
            jar = ((JarURLConnection)url.openConnection()).getJarFile();
        } catch (IOException e) {
            logger.error("Failed to get jar file, url is " + url, e);
            return;
        }

        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!name.startsWith(package2Path) || !name.endsWith(CLASS_SUFFIX) || entry.isDirectory()) {
                continue;
            }
            String fileFullName = name.replace('/', '.');
            String classFullName = fileFullName.substring(0, fileFullName.length() - CLASS_SUFFIX.length());
            try {
                Class clazz = ClassLoaderUtils.loadClass(classFullName);
                classes.add(clazz);
            } catch (ClassNotFoundException e) {
                logger.error("Class not found, class name is  " + classFullName, e);
            }
        }
    }

}
