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
package com.alibaba.compileflow.engine.common;

import org.apache.commons.collections4.CollectionUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author yusu
 */
public class ClassWrapper {

    private static final String BEAN_HOLD_NAME = "$TODO";

    private String name;

    private String shortName;

    private String shortRawName;

    private String packageName;

    private List<ClassWrapper> parameterizedType;

    public static ClassWrapper of(String name) {
        ClassWrapper classWrapper = new ClassWrapper();
        classWrapper.setName(name);
        classWrapper.setShortName(getClassShortName(name));
        classWrapper.setShortRawName(getClassShortRawName(name));
        classWrapper.setPackageName(getClassPackage(name));
        classWrapper.setParameterizedType(getClassParameterizedType(name));
        return classWrapper;
    }

    public static ClassWrapper of(Class clazz) {
        return of(clazz.getName());
    }

    private static String getClassShortName(String name) {

        if (Objects.isNull(name)) {
            return BEAN_HOLD_NAME;
        }
        int index = name.lastIndexOf("<");
        name = index == -1 ? getClassShortRawName(name) : getClassShortRawName(name) + name.substring(index);
        return name;
    }

    private static String getClassShortRawName(String name) {

        if (Objects.isNull(name)) {
            return BEAN_HOLD_NAME;
        }
        name = getRawName(name);
        int index = name.lastIndexOf(".");
        return index == -1 ? name : name.substring(index + 1);
    }

    private static String getClassPackage(String name) {

        if (Objects.isNull(name)) {
            return BEAN_HOLD_NAME;
        }
        name = getRawName(name);
        int index = name.lastIndexOf(".");
        return index == -1 ? null : name.substring(0, index);
    }

    private static String getRawName(String name) {

        if (Objects.isNull(name)) {
            return BEAN_HOLD_NAME;
        }
        int index = name.lastIndexOf("<");
        name = index == -1 ? name : name.substring(0, index);
        return name;
    }

    private static List<ClassWrapper> getClassParameterizedType(String name) {

        if (Objects.isNull(name)) {
            return null;
        }
        int index = name.indexOf("<");
        return index == -1 ? Collections.emptyList() :
            Arrays.stream(name.substring(index + 1, name.lastIndexOf(">")).split(","))
                .map(String::trim).map(ClassWrapper::of).collect(Collectors.toList());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShortName() {
        return shortName;
    }

    private void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getShortRawName() {
        return shortRawName;
    }

    public void setShortRawName(String shortRawName) {
        this.shortRawName = shortRawName;
    }

    public String getPackageName() {
        return packageName;
    }

    private void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public List<ClassWrapper> getParameterizedType() {
        return parameterizedType;
    }

    private void setParameterizedType(List<ClassWrapper> parameterizedType) {
        this.parameterizedType = parameterizedType;
    }

    public String getBaseShortName() {
        StringBuilder sb = new StringBuilder();
        sb.append(shortName);

        if (CollectionUtils.isNotEmpty(parameterizedType)) {
            sb.append("<").append(parameterizedType.stream().map(ClassWrapper::getBaseShortName)
                .collect(Collectors.joining(", "))).append(">");
        }

        return sb.toString();
    }

}
