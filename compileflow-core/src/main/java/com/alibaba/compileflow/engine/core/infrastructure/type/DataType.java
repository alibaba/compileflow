/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements...
 */
package com.alibaba.compileflow.engine.core.infrastructure.type;

import com.alibaba.compileflow.engine.config.ProcessPropertyDefaults;
import com.alibaba.compileflow.engine.config.ProcessPropertyKeys;
import com.alibaba.compileflow.engine.config.ProcessPropertyProvider;
import com.google.common.cache.*;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Primitives;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Enhanced data type utilities for CompileFlow engine.
 *
 * @author yusu
 */
public class DataType {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataType.class);

    // Macro constants
    private static final String MACRO_NOW = "$now";
    private static final String MACRO_ARRAYLIST = "$arrayList";
    private static final String MACRO_HASH_SET = "$hashSet";
    private static final List<String> MACRO_VALUES = Collections.unmodifiableList(
            Arrays.asList(MACRO_NOW, MACRO_ARRAYLIST, MACRO_HASH_SET)
    );

    private static final int MAX_CACHE_SIZE = ProcessPropertyProvider.Cache.getDataTypeMaxSize();

    private static final Cache<String, Class<?>> CLASS_CACHE = CacheBuilder.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .removalListener(new RemovalListener<String, Class<?>>() {
                @Override
                public void onRemoval(RemovalNotification<String, Class<?>> notification) {
                    LOGGER.debug("Evicting Class cache: key={}, cause={}",
                            notification.getKey(), notification.getCause());
                }
            })
            .build();

    private static final Cache<String, String> OBJECT_TYPE_CACHE = CacheBuilder.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .removalListener(new RemovalListener<String, String>() {
                @Override
                public void onRemoval(RemovalNotification<String, String> notification) {
                    LOGGER.debug("Evicting ObjectType cache: key={}, cause={}",
                            notification.getKey(), notification.getCause());
                }
            })
            .build();

    // Performance monitoring (optional, can be disabled in production)
    // Unified configuration source via ProcessPropertyProvider
    private static final boolean ENABLE_STATS = ProcessPropertyProvider.getBoolean(
            ProcessPropertyKeys.Cache.DATATYPE_STATS_ENABLED,
            ProcessPropertyDefaults.Cache.DATATYPE_STATS_ENABLED);
    private static final Map<String, Long> CONVERSION_STATS = ENABLE_STATS ? new ConcurrentHashMap<>() : null;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Map<Class<?>, ConversionType> TYPE_CONVERSION_MAP = new ConcurrentHashMap<>();
    // Pre-computed set for O(1) wrapper type checking
    private static final Set<Class<?>> WRAPPER_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    Boolean.class, Byte.class, Character.class, Double.class,
                    Float.class, Integer.class, Long.class, Short.class
            ))
    );

    static {
        for (ConversionType type : ConversionType.values()) {
            TYPE_CONVERSION_MAP.put(type.getWrapperType(), type);
            if (type.getPrimitiveType() != null) {
                TYPE_CONVERSION_MAP.put(type.getPrimitiveType(), type);
            }
        }

        // Pre-warm commonly used type caches
        preWarmCaches();
    }

    /**
     * Pre-warm caches with commonly used types for better startup performance.
     */
    private static void preWarmCaches() {
        String[] commonTypes = {
                "String", "Integer", "Long", "Boolean", "Double", "Float",
                "int", "long", "boolean", "double", "float",
                "java.lang.String", "java.lang.Integer", "java.lang.Long",
                "java.lang.Boolean", "java.lang.Double", "java.lang.Float"
        };

        for (String type : commonTypes) {
            try {
                getJavaClass(type);
                normalizeToObjectTypeName(type);
            } catch (Exception ignore) {
                // Ignore errors during pre-warming
            }
        }
    }

    /**
     * Resolves Java class from type string with enhanced robustness.
     * Handles arrays, normalizes whitespace, rejects generics, uses safe caching.
     */
    public static Class<?> getJavaClass(String type) {
        if (StringUtils.isBlank(type)) {
            throw new DataTypeException.UnsupportedTypeException(type);
        }

        // Normalize: trim whitespace, extract raw type from generics, handle array spacing
        String normalized = type.trim();

        // Extract raw type from generics: "List<String>" -> "List", "Map<K,V>" -> "Map"
        int genericStart = normalized.indexOf('<');
        if (genericStart >= 0) {
            int genericEnd = findMatchingCloseBracket(normalized, genericStart);
            if (genericEnd >= 0) {
                // Keep everything before '<' and after '>' (handle arrays after generics)
                String beforeGeneric = normalized.substring(0, genericStart);
                String afterGeneric = normalized.substring(genericEnd + 1);
                normalized = beforeGeneric + afterGeneric;
            } else {
                throw new DataTypeException.UnsupportedTypeException("Malformed generic type: " + type);
            }
        }

        // Handle "String []" -> "String[]"
        normalized = normalized.replace(" ", "");
        final String finalNormalized = normalized;

        try {
            return CLASS_CACHE.get(normalized, () -> computeJavaClass(finalNormalized));
        } catch (Exception e) {
            // Fallback to direct computation if cache fails
            LOGGER.warn("Cache lookup for class failed, falling back to direct computation: type={}", normalized, e);
            return computeJavaClass(normalized);
        }
    }

    /**
     * Find matching close bracket for generic types, handling nested generics.
     * Returns -1 if no matching bracket found.
     */
    private static int findMatchingCloseBracket(String str, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static Class<?> computeJavaClass(String type) {
        int index = type.indexOf("[]");
        if (index == -1) {
            return getJavaClassInner(type);
        }

        // Handle array types with dimension limits
        StringBuilder arrayString = new StringBuilder("[");
        String baseType = type.substring(0, index);
        int dimensions = 1;

        while ((index = type.indexOf("[]", index + 2)) >= 0) {
            arrayString.append("[");
            dimensions++;
        }

        if (dimensions > 255) {
            throw new DataTypeException.UnsupportedTypeException(
                    "Array dimensions too deep: " + dimensions + " (Java limit is 255, but practical limit is much lower)");
        }

        Class<?> baseClass = getJavaClassInner(baseType);

        try {
            if (!baseClass.isPrimitive()) {
                return loadClass(arrayString + "L" + baseClass.getName() + ";");
            }
            String baseName = getPrimitiveArraySignature(baseClass);
            return loadClass(arrayString + baseName);
        } catch (Exception ex) {
            throw new DataTypeException.ConversionException("String", "Class", type, ex);
        }
    }

    private static String getPrimitiveArraySignature(Class<?> primitiveClass) {
        if (primitiveClass == boolean.class) {
            return "Z";
        }
        if (primitiveClass == byte.class) {
            return "B";
        }
        if (primitiveClass == char.class) {
            return "C";
        }
        if (primitiveClass == double.class) {
            return "D";
        }
        if (primitiveClass == float.class) {
            return "F";
        }
        if (primitiveClass == int.class) {
            return "I";
        }
        if (primitiveClass == long.class) {
            return "J";
        }
        if (primitiveClass == short.class) {
            return "S";
        }
        throw new DataTypeException.UnsupportedTypeException("Unknown primitive type: " + primitiveClass);
    }

    private static Class<?> getJavaClassInner(String type) {
        Class<?> result = TypeRegistry.getJavaClass(type);
        return result != null ? result : loadClass(type);
    }

    private static Class<?> loadClass(String className) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                // Fallback to system class loader if TCCL is null
                cl = DataType.class.getClassLoader();
            }
            return Class.forName(className, false, cl);
        } catch (ClassNotFoundException e) {
            throw new DataTypeException.ConversionException("String", "Class", className, e);
        }
    }

    /**
     * Get wrapper class for primitive types using Guava Primitives.
     */
    public static Class<?> getWrapperClass(Class<?> type) {
        if (type == null || !type.isPrimitive()) {
            return type;
        }
        return Primitives.wrap(type);
    }

    /**
     * Check if a class is a wrapper type for primitives.
     * Uses pre-computed set for O(1) lookup performance.
     */
    private static boolean isWrapperType(Class<?> clazz) {
        return clazz != null && WRAPPER_TYPES.contains(clazz);
    }

    /**
     * Normalize type name to object type (converts primitives to wrapper types).
     * For primitives: int -> java.lang.Integer, boolean -> java.lang.Boolean
     * For wrapper types: preserves original type name (java.lang.Boolean -> java.lang.Boolean)
     * For others: preserves original type name (java.lang.String -> java.lang.String)
     */
    public static String normalizeToObjectTypeName(String type) {
        if (StringUtils.isBlank(type)) {
            return type;
        }

        try {
            // Use Guava Cache's get method with loading function
            return OBJECT_TYPE_CACHE.get(type, () -> {
                String result;
                TypeInfo typeInfo = TypeRegistry.getTypeInfo(type);

                if (typeInfo != null) {
                    if (typeInfo.isPrimitive()) {
                        // Convert primitive to wrapper: int -> java.lang.Integer, boolean -> java.lang.Boolean
                        Class<?> wrapperClass = TypeRegistry.getWrapperClass(typeInfo.getJavaClass());
                        result = wrapperClass.getName();
                    } else if (typeInfo.isWrapper()) {
                        // Already a wrapper type, return its full name: Boolean -> java.lang.Boolean
                        result = typeInfo.getJavaClass().getName();
                    } else {
                        // Other types (String, Object, etc.), preserve original type name
                        result = type;
                    }
                } else {
                    // Unknown type, try to resolve as Class and check if it's a wrapper
                    try {
                        Class<?> clazz = getJavaClass(type);
                        if (clazz != null && isWrapperType(clazz)) {
                            result = clazz.getName();
                        } else {
                            result = type;
                        }
                    } catch (Exception e) {
                        // Fallback: preserve original type name
                        result = type;
                    }
                }
                return result;
            });
        } catch (Exception e) {
            // Fallback to direct computation if cache fails
            LOGGER.warn("Cache lookup for object type name failed, falling back to direct computation: type={}",
                    type, e);
            return type; // Safe fallback
        }
    }

    /**
     * Get unboxing method name for primitive types.
     * Returns the method name to convert wrapper to primitive (e.g., "intValue()" for int.class).
     */
    public static String getUnboxingMethodName(Class<?> primitiveType) {
        if (primitiveType == null) {
            throw new IllegalArgumentException("Primitive type cannot be null");
        }
        if (!primitiveType.isPrimitive()) {
            throw new IllegalArgumentException("Type " + primitiveType.getName() + " is not a primitive type");
        }

        if (primitiveType == short.class) {
            return "shortValue()";
        }
        if (primitiveType == int.class) {
            return "intValue()";
        }
        if (primitiveType == long.class) {
            return "longValue()";
        }
        if (primitiveType == double.class) {
            return "doubleValue()";
        }
        if (primitiveType == float.class) {
            return "floatValue()";
        }
        if (primitiveType == byte.class) {
            return "byteValue()";
        }
        if (primitiveType == char.class) {
            return "charValue()";
        }
        if (primitiveType == boolean.class) {
            return "booleanValue()";
        }

        throw new IllegalArgumentException("Unsupported primitive type: " + primitiveType.getName());
    }

    /**
     * Generate type conversion code for runtime use.
     * Creates Java code string to convert a variable from source type to destination type.
     */
    public static String generateTypeConversionCode(Class<?> sourceType, Class<?> destType, String varName) {
        if (destType == null) {
            throw new IllegalArgumentException("Destination type cannot be null");
        }
        if (StringUtils.isBlank(varName)) {
            throw new IllegalArgumentException("Variable name cannot be null or empty");
        }

        // If source type is null or same as dest, return as-is
        if (sourceType == null || sourceType.equals(destType)) {
            return varName;
        }

        if (destType.isPrimitive()) {
            Class<?> wrapperClass = getWrapperClass(destType);
            String wrapperName = getSimplifiedClassName(wrapperClass);
            String unboxingMethod = getUnboxingMethodName(destType);
            return "((" + wrapperName + ")DataType.transfer("
                    + varName + ", " + wrapperName + ".class))." + unboxingMethod;
        } else {
            String destTypeName = getSimplifiedClassName(destType);
            return "(" + destTypeName + ")DataType.transfer("
                    + varName + ", " + destTypeName + ".class)";
        }
    }

    /**
     * Generate default value string for code generation.
     */
    public static String getDefaultValueString(Class<?> type, String value) {
        if (value == null || value.trim().isEmpty()) {
            return getNullValueString(type);
        }
        if (MACRO_VALUES.contains(value)) {
            return getMacroValue(value);
        }
        if (value.startsWith("@")) {
            return value.substring(1);
        }

        if (type.isPrimitive()) {
            return getPrimitiveDefaultValue(type, value);
        } else {
            return getObjectDefaultValue(type, value);
        }
    }

    private static String getPrimitiveDefaultValue(Class<?> type, String value) {
        if (type == short.class) {
            return "(short)" + value;
        }
        if (type == int.class) {
            return value;
        }
        if (type == long.class) {
            return value;
        }
        if (type == double.class) {
            return value;
        }
        if (type == float.class) {
            return value;
        }
        if (type == byte.class) {
            return "(byte)" + value;
        }
        if (type == char.class) {
            // For char type, only take the first character
            if (value == null || value.isEmpty()) {
                return "'\\0'";
            }
            char firstChar = value.charAt(0);
            return "'" + StringEscapeUtils.escapeJava(String.valueOf(firstChar)) + "'";
        }
        if (type == boolean.class) {
            try {
                Object b = safeBooleanValue(value);
                return Boolean.TRUE.equals(b) ? "true" : "false";
            } catch (DataTypeException e) {
                throw new DataTypeException.ConversionException("String", "boolean literal", value);
            }
        }
        return value;
    }

    private static String getObjectDefaultValue(Class<?> type, String value) {
        if (type == String.class) {
            return "\"" + StringEscapeUtils.escapeJava(value) + "\"";
        }
        if (type == Short.class) {
            return "Short.valueOf((short)" + value + ")";
        }
        if (type == Integer.class) {
            return "Integer.valueOf(" + value + ")";
        }
        if (type == Long.class) {
            return "Long.valueOf(" + value + ")";
        }
        if (type == Double.class) {
            return "Double.valueOf(" + value + ")";
        }
        if (type == Float.class) {
            return "Float.valueOf(" + value + ")";
        }
        if (type == Byte.class) {
            return "Byte.valueOf((byte)" + value + ")";
        }
        if (type == Character.class) {
            char ch = StringUtils.isNotEmpty(value) ? value.charAt(0) : 0;
            return "Character.valueOf('" + StringEscapeUtils.escapeJava(String.valueOf(ch)) + "')";
        }
        if (type == Boolean.class) {
            return "Boolean.valueOf(" + value + ")";
        }
        return "null";
    }


    private static String getNullValueString(Class<?> type) {
        if (type.isPrimitive()) {
            if (type == boolean.class) {
                return "false";
            }
            if (type == char.class) {
                return "'\\0'";
            }
            return "0";
        }
        return "null";
    }

    private static String getMacroValue(String value) {
        switch (value) {
            case MACRO_NOW:
                return "new java.util.Date()";
            case MACRO_ARRAYLIST:
                return "new java.util.ArrayList()";
            case MACRO_HASH_SET:
                return "new java.util.HashSet()";
            default:
                throw new DataTypeException.InvalidMacroException(value);
        }
    }

    /**
     * Get simplified class name for code generation.
     * Handles arrays and removes java.lang package prefix for common types.
     */
    public static String getSimplifiedClassName(String name) {
        StringBuilder arrays = new StringBuilder();
        if (name.contains("[")) {
            int point = 0;
            while (point < name.length() && name.charAt(point) == '[') {
                arrays.append("[]");
                ++point;
            }
            if (point < name.length()) {
                char c = name.charAt(point);
                if (c == 'L') {
                    name = name.substring(point + 1, name.length() - 1);
                } else if (c == 'Z') {
                    name = "boolean";
                } else if (c == 'B') {
                    name = "byte";
                } else if (c == 'C') {
                    name = "char";
                } else if (c == 'D') {
                    name = "double";
                } else if (c == 'F') {
                    name = "float";
                } else if (c == 'I') {
                    name = "int";
                } else if (c == 'J') {
                    name = "long";
                } else if (c == 'S') {
                    name = "short";
                }
            }
        }
        int index = name.lastIndexOf('.');
        if (index > 0 && "java.lang".equals(name.substring(0, index))) {
            name = name.substring(index + 1);
        }
        return name + arrays;
    }

    public static String getSimplifiedClassName(Class<?> type) {
        return getSimplifiedClassName(type.getName());
    }

    /**
     * Enhanced runtime type conversion with strategy pattern for better performance.
     * This is the core method used extensively in code generation.
     */
    public static Object transfer(Object value, Class<?> type) {
        if (value == null) {
            return null;
        }

        if (value instanceof String && StringUtils.isBlank((String) value)) {
            return String.class.equals(type) ? value : null;
        }

        // Fast path: if value is already of target type
        if (type.isInstance(value)) {
            return value;
        }

        ConversionType conversionType = TYPE_CONVERSION_MAP.get(type);
        if (conversionType != null) {
            try {
                Object result = conversionType.convert(value);
                recordConversion(type.getSimpleName());
                return result;
            } catch (Exception e) {
                if (e instanceof DataTypeException) {
                    throw e;
                }
                throw new DataTypeException.ConversionException("Object", type.getSimpleName(), value, e);
            }
        }

        // Fallback for unknown types
        return value;
    }

    /**
     * Record conversion statistics (if enabled).
     */
    private static void recordConversion(String typeName) {
        if (ENABLE_STATS && CONVERSION_STATS != null) {
            CONVERSION_STATS.merge(typeName, 1L, Long::sum);
        }
    }

    /**
     * Get conversion statistics (for monitoring and debugging).
     * Returns empty map if statistics are disabled.
     */
    public static Map<String, Long> getConversionStats() {
        return ENABLE_STATS && CONVERSION_STATS != null ?
                new ConcurrentHashMap<>(CONVERSION_STATS) : Collections.emptyMap();
    }

    /**
     * Clear conversion statistics.
     */
    public static void clearConversionStats() {
        if (ENABLE_STATS && CONVERSION_STATS != null) {
            CONVERSION_STATS.clear();
        }
    }

    /**
     * Get cache statistics for monitoring and debugging.
     * Returns detailed statistics about cache performance.
     */
    public static Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();

        CacheStats classCacheStats = CLASS_CACHE.stats();
        CacheStats objectTypeCacheStats = OBJECT_TYPE_CACHE.stats();

        stats.put("classCache", ImmutableMap.of(
                "hitCount", classCacheStats.hitCount(),
                "missCount", classCacheStats.missCount(),
                "hitRate", String.format("%.2f%%", classCacheStats.hitRate() * 100),
                "size", CLASS_CACHE.size(),
                "requestCount", classCacheStats.requestCount()
        ));

        stats.put("objectTypeCache", ImmutableMap.of(
                "hitCount", objectTypeCacheStats.hitCount(),
                "missCount", objectTypeCacheStats.missCount(),
                "hitRate", String.format("%.2f%%", objectTypeCacheStats.hitRate() * 100),
                "size", OBJECT_TYPE_CACHE.size(),
                "requestCount", objectTypeCacheStats.requestCount()
        ));

        return stats;
    }

    /**
     * Clear all caches and reset statistics.
     * Useful for testing or memory management.
     */
    public static void clearAllCaches() {
        CLASS_CACHE.invalidateAll();
        OBJECT_TYPE_CACHE.invalidateAll();
        if (ENABLE_STATS && CONVERSION_STATS != null) {
            CONVERSION_STATS.clear();
        }
        LOGGER.info("All DataType caches have been cleared.");
    }

    /**
     * Get current cache sizes for monitoring.
     */
    public static Map<String, Long> getCacheSizes() {
        return ImmutableMap.of(
                "classCacheSize", CLASS_CACHE.size(),
                "objectTypeCacheSize", OBJECT_TYPE_CACHE.size()
        );
    }

    // --------- Safe conversion helpers ---------

    private static Object safeShortValue(Object value) {
        if (value instanceof Short) {
            return value;
        }
        if (value instanceof Number) {
            Number num = (Number) value;
            // Handle all numeric types with proper overflow checking
            if (num instanceof Byte) {
                return num.shortValue();
            }
            if (num instanceof Integer || num instanceof Long) {
                long longVal = num.longValue();
                if (longVal >= Short.MIN_VALUE && longVal <= Short.MAX_VALUE) {
                    return (short) longVal;
                }
                throw new DataTypeException.ConversionException("Object", "Short", value,
                        new ArithmeticException("Value " + longVal + " overflows short range"));
            }
            if (num instanceof Float || num instanceof Double) {
                double doubleVal = num.doubleValue();
                if (doubleVal >= Short.MIN_VALUE && doubleVal <= Short.MAX_VALUE) {
                    return (short) doubleVal;
                }
                throw new DataTypeException.ConversionException("Object", "Short", value,
                        new ArithmeticException("Value " + doubleVal + " overflows short range"));
            }
            if (num instanceof BigDecimal) {
                BigDecimal bd = (BigDecimal) num;
                if (bd.compareTo(BigDecimal.valueOf(Short.MAX_VALUE)) > 0 ||
                        bd.compareTo(BigDecimal.valueOf(Short.MIN_VALUE)) < 0) {
                    throw new DataTypeException.ConversionException("Object", "Short", value,
                            new ArithmeticException("Value " + bd + " overflows short range"));
                }
                return bd.shortValue();
            }
            if (num instanceof BigInteger) {
                BigInteger bi = (BigInteger) num;
                if (bi.compareTo(BigInteger.valueOf(Short.MAX_VALUE)) > 0 ||
                        bi.compareTo(BigInteger.valueOf(Short.MIN_VALUE)) < 0) {
                    throw new DataTypeException.ConversionException("Object", "Short", value,
                            new ArithmeticException("Value " + bi + " overflows short range"));
                }
                return bi.shortValue();
            }
        }

        String str = sanitizeNumber(String.valueOf(value));
        if (!NumberUtils.isCreatable(str)) {
            throw new DataTypeException.ConversionException("Object", "Short", value);
        }
        try {
            // Try direct parsing first for performance
            if (str.indexOf('.') == -1 && str.indexOf('e') == -1 && str.indexOf('E') == -1) {
                long longVal = Long.parseLong(str);
                if (longVal >= Short.MIN_VALUE && longVal <= Short.MAX_VALUE) {
                    return (short) longVal;
                }
                throw new DataTypeException.ConversionException("Object", "Short", value,
                        new ArithmeticException("Value " + longVal + " overflows short range"));
            }
            // Fallback to BigDecimal for decimal values
            BigDecimal bd = NumberUtils.createBigDecimal(str);
            if (bd.compareTo(BigDecimal.valueOf(Short.MAX_VALUE)) > 0 ||
                    bd.compareTo(BigDecimal.valueOf(Short.MIN_VALUE)) < 0) {
                throw new DataTypeException.ConversionException("Object", "Short", value,
                        new ArithmeticException("Value " + bd + " overflows short range"));
            }
            return bd.shortValue();
        } catch (NumberFormatException e) {
            throw new DataTypeException.ConversionException("Object", "Short", value, e);
        }
    }

    private static Object safeIntValue(Object value) {
        if (value instanceof Integer) {
            return value;
        }
        String str = sanitizeNumber(String.valueOf(value));
        if (!NumberUtils.isCreatable(str)) {
            throw new DataTypeException.ConversionException("Object", "Integer", value);
        }
        try {
            BigDecimal bd = NumberUtils.createBigDecimal(str);
            if (bd.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0 ||
                    bd.compareTo(BigDecimal.valueOf(Integer.MIN_VALUE)) < 0) {
                throw new DataTypeException.ConversionException("Object", "Integer", value);
            }
            return bd.intValue();
        } catch (NumberFormatException e) {
            throw new DataTypeException.ConversionException("Object", "Integer", value, e);
        }
    }

    private static Object safeLongValue(Object value) {
        if (value instanceof Long) {
            return value;
        }
        String str = sanitizeNumber(String.valueOf(value));
        if (!NumberUtils.isCreatable(str)) {
            throw new DataTypeException.ConversionException("Object", "Long", value);
        }
        try {
            BigDecimal bd = NumberUtils.createBigDecimal(str);
            if (bd.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0 ||
                    bd.compareTo(BigDecimal.valueOf(Long.MIN_VALUE)) < 0) {
                throw new DataTypeException.ConversionException("Object", "Long", value);
            }
            return bd.longValue();
        } catch (NumberFormatException e) {
            throw new DataTypeException.ConversionException("Object", "Long", value, e);
        }
    }

    private static Object safeByteValue(Object value) {
        if (value instanceof Byte) {
            return value;
        }
        String str = sanitizeNumber(String.valueOf(value));
        if (!NumberUtils.isCreatable(str)) {
            throw new DataTypeException.ConversionException("Object", "Byte", value);
        }
        try {
            BigDecimal bd = NumberUtils.createBigDecimal(str);
            if (bd.compareTo(BigDecimal.valueOf(Byte.MAX_VALUE)) > 0 ||
                    bd.compareTo(BigDecimal.valueOf(Byte.MIN_VALUE)) < 0) {
                throw new DataTypeException.ConversionException("Object", "Byte", value);
            }
            return bd.byteValue();
        } catch (NumberFormatException e) {
            throw new DataTypeException.ConversionException("Object", "Byte", value, e);
        }
    }

    private static Object safeDoubleValue(Object value) {
        if (value instanceof Double) {
            return value;
        }
        String str = sanitizeNumber(String.valueOf(value));
        if (!NumberUtils.isCreatable(str)) {
            throw new DataTypeException.ConversionException("Object", "Double", value);
        }
        try {
            double result = NumberUtils.createDouble(str);
            if (Double.isInfinite(result) || Double.isNaN(result)) {
                throw new DataTypeException.ConversionException("Object", "Double", value);
            }
            return result;
        } catch (NumberFormatException e) {
            throw new DataTypeException.ConversionException("Object", "Double", value, e);
        }
    }

    private static Object safeFloatValue(Object value) {
        if (value instanceof Float) {
            return value;
        }
        String str = sanitizeNumber(String.valueOf(value));
        if (!NumberUtils.isCreatable(str)) {
            throw new DataTypeException.ConversionException("Object", "Float", value);
        }
        try {
            float result = NumberUtils.createFloat(str);
            if (Float.isInfinite(result) || Float.isNaN(result)) {
                throw new DataTypeException.ConversionException("Object", "Float", value);
            }
            return result;
        } catch (NumberFormatException e) {
            throw new DataTypeException.ConversionException("Object", "Float", value, e);
        }
    }

    private static Object safeBigDecimalValue(Object value) {
        if (value instanceof BigDecimal) {
            return value;
        }
        String s = StringUtils.trimToNull(String.valueOf(value));
        if (s == null) {
            throw new DataTypeException.ConversionException("Object", "BigDecimal", value);
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new DataTypeException.ConversionException("Object", "BigDecimal", value, e);
        }
    }

    private static Object safeBigIntegerValue(Object value) {
        if (value instanceof BigInteger) {
            return value;
        }
        String s = StringUtils.trimToNull(String.valueOf(value));
        if (s == null) {
            throw new DataTypeException.ConversionException("Object", "BigInteger", value);
        }
        try {
            return new BigInteger(s);
        } catch (NumberFormatException e) {
            throw new DataTypeException.ConversionException("Object", "BigInteger", value, e);
        }
    }

    private static Object safeBooleanValue(Object value) {
        if (value instanceof Boolean) {
            return value;
        }

        if (value instanceof Number) {
            // Handle different number types properly
            Number num = (Number) value;
            if (num instanceof Double || num instanceof Float) {
                return num.doubleValue() != 0.0d;
            } else {
                return num.longValue() != 0L;
            }
        }

        if (value instanceof String) {
            String s = StringUtils.trimToNull((String) value);
            if (s == null) {
                return Boolean.FALSE; // Empty string is false, not an error
            }

            // Use Apache Commons BooleanUtils first
            Boolean result = BooleanUtils.toBooleanObject(s);
            if (result != null) {
                return result;
            }

            // Extended boolean representations
            String lower = s.toLowerCase();
            if ("y".equals(lower) || "yes".equals(lower) || "1".equals(s) ||
                    "true".equals(lower) || "t".equals(lower) || "on".equals(lower)) {
                return Boolean.TRUE;
            }
            if ("n".equals(lower) || "no".equals(lower) || "0".equals(s) ||
                    "false".equals(lower) || "f".equals(lower) || "off".equals(lower)) {
                return Boolean.FALSE;
            }

            // Try to parse as number
            try {
                double d = Double.parseDouble(s);
                return d != 0.0d;
            } catch (NumberFormatException ignore) {
                // Not a number, fall through to error
            }
        }

        throw new DataTypeException.ConversionException("Object", "Boolean", value);
    }

    private static Object safeCharValue(Object value) {
        if (value instanceof Character) {
            return value;
        }
        String str = String.valueOf(value);
        if (str.isEmpty()) {
            throw new DataTypeException.ConversionException("Object", "Character", value);
        }
        return str.charAt(0);
    }

    private static Object safeStringValue(Object value) {
        return value instanceof String ? value : String.valueOf(value);
    }

    private static Object safeSqlDateValue(Object value) {
        if (value instanceof java.sql.Date) {
            return value;
        }
        if (value instanceof java.util.Date) {
            return new java.sql.Date(((java.util.Date) value).getTime());
        }
        return parseSqlDate(String.valueOf(value));
    }

    private static Object safeSqlTimeValue(Object value) {
        if (value instanceof java.sql.Time) {
            return value;
        }
        if (value instanceof java.util.Date) {
            return new java.sql.Time(((java.util.Date) value).getTime());
        }
        return parseSqlTime(String.valueOf(value));
    }

    private static Object safeSqlTimestampValue(Object value) {
        if (value instanceof java.sql.Timestamp) {
            return value;
        }
        if (value instanceof java.util.Date) {
            return new java.sql.Timestamp(((java.util.Date) value).getTime());
        }
        return parseSqlTimestamp(String.valueOf(value));
    }

    private static Object safeUtilDateValue(Object value) {
        return parseUtilDate(String.valueOf(value));
    }

    private static String sanitizeNumber(String s) {
        if (s == null) {
            return "";
        }
        // Support "1_000" / "1,000" and remove whitespace
        return StringUtils.trimToEmpty(s)
                .replace("_", "")
                .replace(",", "")
                .replace(" ", ""); // Remove internal spaces like "1 000"
    }

    // ---- Date parsing: add millisecond/ISO8601 fallback, keep minimal implementation ----

    private static java.sql.Date parseSqlDate(String s) {
        String trimmed = StringUtils.trimToNull(s);
        if (trimmed == null) {
            throw new DataTypeException.ConversionException("String", "Date", s);
        }
        try {
            return java.sql.Date.valueOf(trimmed);
        } catch (IllegalArgumentException e) {
            try {
                LocalDate localDate = LocalDate.parse(trimmed, DATE_FORMATTER);
                return java.sql.Date.valueOf(localDate);
            } catch (DateTimeParseException ex1) {
                // ISO8601 (date)
                try {
                    Instant ins = Instant.parse(trimmed);
                    return new java.sql.Date(ins.toEpochMilli());
                } catch (Exception ignore) {
                }
                try {
                    return new java.sql.Date(new SimpleDateFormat("yyyy-MM-dd").parse(trimmed).getTime());
                } catch (ParseException ex2) {
                    throw new DataTypeException.ConversionException("String", "Date", s, ex2);
                }
            }
        }
    }

    private static java.sql.Time parseSqlTime(String s) {
        String trimmed = StringUtils.trimToNull(s);
        if (trimmed == null) {
            throw new DataTypeException.ConversionException("String", "Time", s);
        }
        try {
            return java.sql.Time.valueOf(trimmed);
        } catch (IllegalArgumentException e) {
            try {
                LocalTime localTime = LocalTime.parse(trimmed, TIME_FORMATTER);
                return java.sql.Time.valueOf(localTime);
            } catch (DateTimeParseException ex1) {
                // Milliseconds
                try {
                    return new java.sql.Time(new SimpleDateFormat("HH:mm:ss.SSS").parse(trimmed).getTime());
                } catch (ParseException ignore) {
                }
                try {
                    return new java.sql.Time(new SimpleDateFormat("HH:mm:ss").parse(trimmed).getTime());
                } catch (ParseException ex2) {
                    throw new DataTypeException.ConversionException("String", "Time", s, ex2);
                }
            }
        }
    }

    private static java.sql.Timestamp parseSqlTimestamp(String s) {
        String trimmed = StringUtils.trimToNull(s);
        if (trimmed == null) {
            throw new DataTypeException.ConversionException("String", "Timestamp", s);
        }
        try {
            return java.sql.Timestamp.valueOf(trimmed);
        } catch (IllegalArgumentException e) {
            try {
                LocalDateTime localDateTime = LocalDateTime.parse(trimmed, DATETIME_FORMATTER);
                return java.sql.Timestamp.valueOf(localDateTime);
            } catch (DateTimeParseException ex1) {
                // Milliseconds
                try {
                    return new java.sql.Timestamp(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").parse(trimmed).getTime());
                } catch (ParseException ignore) {
                }
                // ISO8601
                try {
                    Instant ins = Instant.parse(trimmed);
                    return new java.sql.Timestamp(ins.toEpochMilli());
                } catch (Exception ignore) {
                }
                try {
                    return new java.sql.Timestamp(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(trimmed).getTime());
                } catch (ParseException ex2) {
                    throw new DataTypeException.ConversionException("String", "Timestamp", s, ex2);
                }
            }
        }
    }

    private static java.util.Date parseUtilDate(String s) {
        String trimmed = StringUtils.trimToNull(s);
        if (trimmed == null) {
            throw new DataTypeException.ConversionException("String", "Date", s);
        }
        try {
            LocalDateTime ldt = LocalDateTime.parse(trimmed, DATETIME_FORMATTER);
            return java.util.Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException e1) {
            try {
                LocalDate ld = LocalDate.parse(trimmed, DATE_FORMATTER);
                return java.util.Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
            } catch (DateTimeParseException e2) {
                // Milliseconds
                try {
                    return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").parse(trimmed);
                } catch (ParseException ignore) {
                }
                // ISO8601
                try {
                    return java.util.Date.from(Instant.parse(trimmed));
                } catch (Exception ignore) {
                }
                // Final fallback: try common non-standard formats
                try {
                    return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").parse(trimmed);
                } catch (ParseException e3) {
                    try {
                        return new SimpleDateFormat("dd/MM/yyyy").parse(trimmed);
                    } catch (ParseException e4) {
                        throw new DataTypeException.ConversionException("String", "Date", s, e4);
                    }
                }
            }
        }
    }

    // Type conversion strategy pattern for O(1) lookup
    private enum ConversionType {
        SHORT(Short.class, short.class, DataType::safeShortValue),
        INTEGER(Integer.class, int.class, DataType::safeIntValue),
        LONG(Long.class, long.class, DataType::safeLongValue),
        DOUBLE(Double.class, double.class, DataType::safeDoubleValue),
        FLOAT(Float.class, float.class, DataType::safeFloatValue),
        BYTE(Byte.class, byte.class, DataType::safeByteValue),
        BOOLEAN(Boolean.class, boolean.class, DataType::safeBooleanValue),
        CHARACTER(Character.class, char.class, DataType::safeCharValue),
        STRING(String.class, null, DataType::safeStringValue),
        SQL_DATE(java.sql.Date.class, null, DataType::safeSqlDateValue),
        SQL_TIME(java.sql.Time.class, null, DataType::safeSqlTimeValue),
        SQL_TIMESTAMP(java.sql.Timestamp.class, null, DataType::safeSqlTimestampValue),
        UTIL_DATE(java.util.Date.class, null, DataType::safeUtilDateValue),
        BIG_DECIMAL(BigDecimal.class, null, DataType::safeBigDecimalValue),
        BIG_INTEGER(BigInteger.class, null, DataType::safeBigIntegerValue);

        private final Class<?> wrapperType;
        private final Class<?> primitiveType;
        private final Function<Object, Object> converter;

        ConversionType(Class<?> wrapperType, Class<?> primitiveType, Function<Object, Object> converter) {
            this.wrapperType = wrapperType;
            this.primitiveType = primitiveType;
            this.converter = converter;
        }

        public Class<?> getWrapperType() {
            return wrapperType;
        }

        public Class<?> getPrimitiveType() {
            return primitiveType;
        }

        public Object convert(Object value) {
            return converter.apply(value);
        }
    }
}
