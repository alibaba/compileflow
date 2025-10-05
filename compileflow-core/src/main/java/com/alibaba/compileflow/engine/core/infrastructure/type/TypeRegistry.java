/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements...
 */
package com.alibaba.compileflow.engine.core.infrastructure.type;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified type registry that consolidates all type-related operations.
 *
 * @author yusu
 */
public class TypeRegistry {

    private static final Map<String, TypeInfo> TYPE_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, String> TYPE_ALIASES = new ConcurrentHashMap<>();

    static {
        initializeTypeRegistry();
        initializeTypeAliases();
    }

    private static void initializeTypeRegistry() {
        // String type
        registerType("String", String.class, null, false, "null", "\"\"", "");
        registerType("java.lang.String", String.class, null, false, "null", "\"\"", "");

        // Wrapper types (use exact class names)
        registerType("Short", Short.class, short.class, true, "(short)0", "(short)0", "shortValue");
        registerType("java.lang.Short", Short.class, short.class, true, "(short)0", "(short)0", "shortValue");
        registerType("Integer", Integer.class, int.class, true, "0", "0", "intValue");
        registerType("java.lang.Integer", Integer.class, int.class, true, "0", "0", "intValue");
        registerType("Long", Long.class, long.class, true, "0", "0", "longValue");
        registerType("java.lang.Long", Long.class, long.class, true, "0", "0", "longValue");
        registerType("Double", Double.class, double.class, true, "0", "0", "doubleValue");
        registerType("java.lang.Double", Double.class, double.class, true, "0", "0", "doubleValue");
        registerType("Float", Float.class, float.class, true, "0", "0", "floatValue");
        registerType("java.lang.Float", Float.class, float.class, true, "0", "0", "floatValue");
        registerType("Byte", Byte.class, byte.class, true, "((byte)0)", "(byte)0", "byteValue");
        registerType("java.lang.Byte", Byte.class, byte.class, true, "((byte)0)", "(byte)0", "byteValue");
        registerType("Character", Character.class, char.class, true, "((char)0)", "(char)0", "charValue");
        registerType("java.lang.Character", Character.class, char.class, true, "((char)0)", "(char)0", "charValue");
        registerType("Boolean", Boolean.class, boolean.class, true, "false", "false", "booleanValue");
        registerType("java.lang.Boolean", Boolean.class, boolean.class, true, "false", "false", "booleanValue");

        // Primitive types
        registerType("short", short.class, short.class, true, "(short)0", "(short)0", "shortValue");
        registerType("int", int.class, int.class, true, "0", "0", "intValue");
        registerType("long", long.class, long.class, true, "0", "0", "longValue");
        registerType("double", double.class, double.class, true, "0", "0", "doubleValue");
        registerType("float", float.class, float.class, true, "0", "0", "floatValue");
        registerType("byte", byte.class, byte.class, true, "(byte)0", "(byte)0", "byteValue");
        registerType("char", char.class, char.class, true, "(char)0", "(char)0", "charValue");
        registerType("boolean", boolean.class, boolean.class, true, "false", "false", "booleanValue");

        // Date/Time types
        registerType("Date", java.sql.Date.class, null, false, "null", "null", "");
        registerType("java.sql.Date", java.sql.Date.class, null, false, "null", "null", "");
        registerType("Time", java.sql.Time.class, null, false, "null", "null", "");
        registerType("java.sql.Time", java.sql.Time.class, null, false, "null", "null", "");
        registerType("Timestamp", java.sql.Timestamp.class, null, false, "null", "null", "");
        registerType("java.sql.Timestamp", java.sql.Timestamp.class, null, false, "null", "null", "");
        registerType("java.util.Date", java.util.Date.class, null, false, "null", "null", "");

        // Object type
        registerType("Object", Object.class, null, false, "null", "null", "");
        registerType("java.lang.Object", Object.class, null, false, "null", "null", "");
    }

    private static void initializeTypeAliases() {
        // Legacy aliases for backward compatibility only
        TYPE_ALIASES.put("datetime", "java.sql.Timestamp");
        TYPE_ALIASES.put("timestamp", "java.sql.Timestamp");
        TYPE_ALIASES.put("bigdecimal", "java.math.BigDecimal");
        TYPE_ALIASES.put("biginteger", "java.math.BigInteger");
    }

    private static void registerType(String typeName, Class<?> javaClass, Class<?> primitiveClass,
                                     boolean isSimple, String defaultValue, String nullValueString,
                                     String transferFunction) {
        TypeInfo typeInfo = new TypeInfo(typeName, javaClass, primitiveClass, isSimple,
                defaultValue, nullValueString, transferFunction);
        // Store with exact key to avoid conflicts
        TYPE_REGISTRY.put(typeName, typeInfo);
    }

    /**
     * Get type information by type name with intelligent lookup.
     * Tries exact match first, then case-insensitive fallback.
     */
    public static TypeInfo getTypeInfo(String typeName) {
        if (typeName == null) {
            return null;
        }

        // 1. Try exact match first (most common case)
        TypeInfo typeInfo = TYPE_REGISTRY.get(typeName);
        if (typeInfo != null) {
            return typeInfo;
        }

        // 2. Try case-insensitive lookup for user convenience
        String normalizedName = typeName.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, TypeInfo> entry : TYPE_REGISTRY.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).equals(normalizedName)) {
                return entry.getValue();
            }
        }

        // 3. Try alias lookup (for backward compatibility)
        String aliasTarget = TYPE_ALIASES.get(normalizedName);
        if (aliasTarget != null) {
            TypeInfo ti = TYPE_REGISTRY.get(aliasTarget);
            if (ti != null) {
                return ti;
            }
            try {
                Class<?> c = Class.forName(aliasTarget);
                return new TypeInfo(aliasTarget, c, null, false, "null", "null", "");
            } catch (Throwable ignore) {
            }
        }

        return null;
    }

    /**
     * Get Java class for a type name.
     */
    public static Class<?> getJavaClass(String typeName) {
        TypeInfo typeInfo = getTypeInfo(typeName);
        return typeInfo != null ? typeInfo.getJavaClass() : null;
    }

    /**
     * Check if a type is a simple data type.
     */
    public static boolean isSimpleDataType(String typeName) {
        TypeInfo typeInfo = getTypeInfo(typeName);
        return typeInfo != null && typeInfo.isSimple();
    }

    /**
     * Get the wrapper class for a primitive type.
     */
    public static Class<?> getWrapperClass(Class<?> primitiveType) {
        if (primitiveType == null || !primitiveType.isPrimitive()) {
            return primitiveType;
        }
        for (TypeInfo typeInfo : TYPE_REGISTRY.values()) {
            if (typeInfo.getPrimitiveClass() == primitiveType && typeInfo.isWrapper()) {
                return typeInfo.getJavaClass();
            }
        }
        return primitiveType;
    }

    /**
     * Get the primitive class for a wrapper type.
     */
    public static Class<?> getPrimitiveClass(Class<?> wrapperType) {
        if (wrapperType == null || wrapperType.isPrimitive()) {
            return wrapperType;
        }
        for (TypeInfo typeInfo : TYPE_REGISTRY.values()) {
            if (typeInfo.getJavaClass().equals(wrapperType) && typeInfo.getPrimitiveClass() != null) {
                return typeInfo.getPrimitiveClass();
            }
        }
        return wrapperType;
    }

    /**
     * Get default value string for a type.
     */
    public static String getDefaultValueString(String typeName) {
        TypeInfo typeInfo = getTypeInfo(typeName);
        return typeInfo != null ? typeInfo.getDefaultValue() : "null";
    }

    /**
     * Get null value string for a type.
     */
    public static String getNullValueString(String typeName) {
        TypeInfo typeInfo = getTypeInfo(typeName);
        return typeInfo != null ? typeInfo.getNullValueString() : "null";
    }

    /**
     * Get transfer function for a type.
     */
    public static String getTransferFunction(String typeName) {
        TypeInfo typeInfo = getTypeInfo(typeName);
        return typeInfo != null ? typeInfo.getTransferFunction() : "";
    }

    /**
     * Get Java object type name (wrapper for primitives).
     */
    public static String getJavaObjectType(String typeName) {
        TypeInfo typeInfo = getTypeInfo(typeName);
        if (typeInfo == null) {
            return typeName;
        }
        if (typeInfo.isPrimitive()) {
            Class<?> wrapperClass = getWrapperClass(typeInfo.getJavaClass());
            return wrapperClass.getSimpleName();
        }
        return typeInfo.getTypeName();
    }

    /**
     * Convert wrapper type to simple type name.
     */
    public static String getSimpleDataType(String typeName) {
        TypeInfo typeInfo = getTypeInfo(typeName);
        if (typeInfo == null) {
            return typeName;
        }
        if (typeInfo.isWrapper() && typeInfo.getPrimitiveClass() != null) {
            return typeInfo.getPrimitiveClass().getSimpleName();
        }
        return typeInfo.getTypeName();
    }

}
