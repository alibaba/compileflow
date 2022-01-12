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

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

/**
 * @author yusu
 */
public class ClassUtils {

    public static <T> T newInstance(String className) throws Exception {
        return (T)newInstance(ClassLoaderUtils.loadClass(className));
    }

    public static <T> T newInstance(String className, Class referrer) throws Exception {
        return newInstance(ClassLoaderUtils.loadClass(className, referrer));
    }

    public static <T> T newInstance(String className, ClassLoader classLoader) throws Exception {
        return newInstance(ClassLoaderUtils.loadClass(className, classLoader));
    }

    public static <T> T newInstance(Class clazz) throws Exception {
        Constructor constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);
        return (T)constructor.newInstance();
    }

    public static boolean isAbstractOrInterface(Class<?> clazz) {
        if (clazz == null) {
            return false;
        }
        return Modifier.isAbstract(clazz.getModifiers()) || Modifier.isInterface(clazz.getModifiers());
    }

}
