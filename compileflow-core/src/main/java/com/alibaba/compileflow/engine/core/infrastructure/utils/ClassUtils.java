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

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.ErrorCode;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Constructor;

/**
 * A utility class for common reflective operations on classes.
 *
 * @author yusu
 */
public class ClassUtils {

    /**
     * Creates a new instance of a class by its name with enhanced error handling.
     * This method provides the same functionality as the removed ObjectFactory.
     *
     * @param className The fully qualified name of the class to instantiate.
     * @param <T>       The expected type of the new instance.
     * @return A new instance of the specified class.
     * @throws CompileFlowException.SystemException If the class cannot be found, instantiated, or accessed.
     */
    public static <T> T newInstance(String className) {
        if (StringUtils.isEmpty(className)) {
            throw new IllegalArgumentException("Class name cannot be null or empty");
        }
        try {
            return newInstance(className.trim(), null);
        } catch (Exception e) {
            throw new CompileFlowException.SystemException(
                    ErrorCode.CF_REFLECTION_001,
                    "Failed to create instance of class: " + className,
                    e
            );
        }
    }

    /**
     * Creates a new instance of a class by its name using a specific {@link ClassLoader}.
     * This method throws checked exceptions for advanced use cases.
     *
     * @param className   The fully qualified name of the class to instantiate.
     * @param classLoader The ClassLoader to use for loading the class. If null, it
     *                    defaults to the current thread's context ClassLoader.
     * @param <T>         The expected type of the new instance.
     * @return A new instance of the specified class.
     * @throws Exception If the class cannot be found, instantiated, or accessed.
     */
    public static <T> T newInstance(String className, ClassLoader classLoader) throws Exception {
        if (classLoader == null) {
            classLoader = Thread.currentThread().getContextClassLoader();
        }
        Class<?> clazz = classLoader.loadClass(className);
        return (T) newInstance(clazz);
    }

    /**
     * Creates a new instance of a class from its {@link Class} object.
     * <p>
     * This method uses the class's default (zero-argument) constructor. It makes
     * the constructor accessible, allowing instantiation of classes with non-public
     * default constructors.
     *
     * @param clazz The class to instantiate.
     * @param <T>   The type of the new instance.
     * @return A new instance of the class.
     * @throws Exception If the default constructor is not found or if instantiation fails.
     */
    public static <T> T newInstance(Class<T> clazz) throws Exception {
        Constructor<T> constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

}
