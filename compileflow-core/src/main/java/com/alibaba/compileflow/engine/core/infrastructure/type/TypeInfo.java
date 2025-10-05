/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements...
 */
package com.alibaba.compileflow.engine.core.infrastructure.type;

/**
 * Encapsulates information about a data type including its Java class,
 * whether it's a simple type, default value, and other metadata.
 *
 * @author yusu
 */
public class TypeInfo {
    private final String typeName;
    private final Class<?> javaClass;
    private final Class<?> primitiveClass;
    private final boolean isSimple;
    private final String defaultValue;
    private final String nullValueString;
    private final String transferFunction;

    public TypeInfo(String typeName, Class<?> javaClass, Class<?> primitiveClass,
                    boolean isSimple, String defaultValue, String nullValueString,
                    String transferFunction) {
        this.typeName = typeName;
        this.javaClass = javaClass;
        this.primitiveClass = primitiveClass;
        this.isSimple = isSimple;
        this.defaultValue = defaultValue;
        this.nullValueString = nullValueString;
        this.transferFunction = transferFunction;
    }

    public String getTypeName() {
        return typeName;
    }

    public Class<?> getJavaClass() {
        return javaClass;
    }

    public Class<?> getPrimitiveClass() {
        return primitiveClass;
    }

    public boolean isSimple() {
        return isSimple;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public String getNullValueString() {
        return nullValueString;
    }

    public String getTransferFunction() {
        return transferFunction;
    }

    public boolean isPrimitive() {
        return primitiveClass != null && javaClass.isPrimitive();
    }

    public boolean isWrapper() {
        return primitiveClass != null && !javaClass.isPrimitive();
    }
}
