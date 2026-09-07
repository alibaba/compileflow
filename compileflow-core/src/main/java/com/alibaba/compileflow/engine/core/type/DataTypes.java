/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.core.type;

import com.google.common.primitives.Primitives;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;

/**
 * Utilities for converting and describing process data types.
 *
 * @author yusu
 */
public final class DataTypes {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_TIME;
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final Map<Class<?>, ConversionType> TYPE_CONVERSION_MAP = createConversionTypes();

    private DataTypes() {
    }

    private static Map<Class<?>, ConversionType> createConversionTypes() {
        Map<Class<?>, ConversionType> conversionTypes = new LinkedHashMap<>();
        for (ConversionType type : ConversionType.values()) {
            conversionTypes.put(type.getWrapperType(), type);
            if (type.getPrimitiveType() != null) {
                conversionTypes.put(type.getPrimitiveType(), type);
            }
        }
        return Map.copyOf(conversionTypes);
    }

    public static Class<?> getJavaClass(String type) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = DataTypes.class.getClassLoader();
        }
        return getJavaClass(type, classLoader);
    }

    /**
     * Resolves a process data type against one exact application ClassLoader.
     */
    public static Class<?> getJavaClass(String type, ClassLoader classLoader) {
        if (StringUtils.isBlank(type)) {
            throw new DataTypeException.UnsupportedTypeException(type);
        }
        ClassLoader loader = Objects.requireNonNull(classLoader, "classLoader");
        JavaTypeParser.ParsedType parsedType = JavaTypeParser.parse(type);
        for (JavaTypeParser.TypeReference reference : parsedType.typeReferences()) {
            Class<?> referencedType = getJavaClassInner(reference.rawName(), loader);
            if (reference.argumentCount() > 0 && referencedType.getTypeParameters().length != reference.argumentCount()) {
                throw new DataTypeException.UnsupportedTypeException(reference.rawName(),
                        "expected " + referencedType.getTypeParameters().length + " generic arguments but found " + reference.argumentCount());
            }
        }
        // Class identity includes the defining ClassLoader. A JVM-global cache keyed only by the
        // type name can return a class from another application or plugin loader and pin that
        // loader in memory. Class loaders already cache their own definitions, so resolve against
        // the current compilation scope directly.
        return computeJavaClass(parsedType, loader);
    }

    /**
     * Returns the validated raw Java source type while preserving array dimensions.
     *
     * <p>This is useful for generated validation wrappers that need Java's erased
     * parameter type but must still reject malformed generic declarations in the
     * original process model.</p>
     *
     * @param type process-model Java type
     * @return raw qualified type plus any array suffix
     */
    public static String getRawTypeName(String type) {
        if (StringUtils.isBlank(type)) {
            throw new DataTypeException.UnsupportedTypeException(type);
        }
        JavaTypeParser.ParsedType parsedType = JavaTypeParser.parse(type);
        return parsedType.rawName() + "[]".repeat(parsedType.arrayDimensions());
    }

    /**
     * Returns the direct generic arguments of a syntactically valid Java source type.
     *
     * <p>Nested arguments remain part of their containing source string. Whitespace is removed so
     * callers can compare declarations without depending on author formatting.</p>
     *
     * @param type process-model Java type
     * @return immutable direct argument list, or an empty list for a raw type
     */
    public static List<String> getTypeArguments(String type) {
        if (StringUtils.isBlank(type)) {
            throw new DataTypeException.UnsupportedTypeException(type);
        }
        return JavaTypeParser.parse(type).typeArguments();
    }

    private static Class<?> computeJavaClass(JavaTypeParser.ParsedType parsedType, ClassLoader classLoader) {
        Class<?> baseClass = getJavaClassInner(parsedType.rawName(), classLoader);
        if (parsedType.arrayDimensions() == 0) {
            return baseClass;
        }
        try {
            return Array.newInstance(baseClass, new int[parsedType.arrayDimensions()]).getClass();
        } catch (IllegalArgumentException failure) {
            throw new DataTypeException.UnsupportedTypeException(parsedType.rawName(), "invalid array type");
        }
    }

    private static Class<?> getJavaClassInner(String type, ClassLoader classLoader) {
        Class<?> result = TypeRegistry.getJavaClass(type);
        return result != null ? result : loadClass(type, classLoader);
    }

    private static Class<?> loadClass(String sourceName, ClassLoader classLoader) {
        // Process definitions use Java source (canonical) names, while
        // Class.forName requires JVM binary names for member classes. Try the
        // source name first, then progressively replace member separators from
        // right to left. Top-level classes normally succeed on the first try.
        String candidate = sourceName;
        while (true) {
            try {
                return Class.forName(candidate, false, classLoader);
            } catch (ClassNotFoundException notFound) {
                int separator = candidate.lastIndexOf('.');
                if (separator < 0) {
                    throw new DataTypeException.UnsupportedTypeException(sourceName,
                            "class is not visible to the current compilation class loader");
                }
                candidate = candidate.substring(0, separator) + '$' + candidate.substring(separator + 1);
            } catch (LinkageError | SecurityException failure) {
                throw new DataTypeException.UnsupportedTypeException(sourceName,
                        "class cannot be linked by the current compilation class loader");
            }
        }
    }

    public static Class<?> getWrapperClass(Class<?> type) {
        if (type == null || !type.isPrimitive()) {
            return type;
        }
        return Primitives.wrap(type);
    }

    public static String normalizeToObjectTypeName(String type) {
        if (StringUtils.isBlank(type)) {
            return type;
        }

        Class<?> known = TypeRegistry.getJavaClass(type);
        if (known != null && (known.isPrimitive() || Primitives.isWrapperType(known))) {
            return Primitives.wrap(known).getName();
        }
        return type;
    }

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
     * Returns whether Java can assign the declared source type without a runtime conversion.
     */
    public static boolean isJavaAssignmentCompatible(Class<?> sourceType, Class<?> destType) {
        if (sourceType.equals(destType)) {
            return true;
        }
        if (!destType.isPrimitive()) {
            Class<?> sourceReference = getWrapperClass(sourceType);
            return destType.isAssignableFrom(sourceReference);
        }

        Class<?> sourcePrimitive = sourceType.isPrimitive() ? sourceType : Primitives.unwrap(sourceType);
        return isPrimitiveWideningConversion(sourcePrimitive, destType);
    }

    private static boolean isPrimitiveWideningConversion(Class<?> sourceType, Class<?> destType) {
        if (sourceType == null || !sourceType.isPrimitive() || !destType.isPrimitive()) {
            return false;
        }
        if (sourceType == destType) {
            return true;
        }
        if (sourceType == byte.class) {
            return destType == short.class || destType == int.class || destType == long.class || destType == float.class
                    || destType == double.class;
        }
        if (sourceType == short.class) {
            return destType == int.class || destType == long.class || destType == float.class
                    || destType == double.class;
        }
        if (sourceType == char.class) {
            return destType == int.class || destType == long.class || destType == float.class
                    || destType == double.class;
        }
        if (sourceType == int.class) {
            return destType == long.class || destType == float.class || destType == double.class;
        }
        if (sourceType == long.class) {
            return destType == float.class || destType == double.class;
        }
        // float -> double is a Java widening conversion, but keeping it on
        // the strict converter path preserves the public finite-number rule
        // for Float.NaN and infinities.
        return false;
    }

    public static DefaultValueCode generateDefaultValueCode(Class<?> type, String value) {
        Class<?> targetType = Objects.requireNonNull(type, "type");
        if (value == null
                || (value.isBlank() && targetType != String.class && targetType != char.class
                && targetType != Character.class)) {
            return defaultValueCode(targetType, getNullValueString(targetType));
        }
        try {
            if (targetType.isPrimitive()) {
                return defaultValueCode(targetType, getPrimitiveDefaultValue(targetType, value));
            }
            return getObjectDefaultValue(targetType, value);
        } catch (DataTypeException.UnsupportedTypeException failure) {
            throw failure;
        } catch (DataTypeException.ConversionException failure) {
            // Parsing helpers report their immediate representation
            // (for example LocalDate). At the public boundary, diagnostics
            // must identify the type declared by the process definition.
            throw new DataTypeException.ConversionException("String", targetType.getName() + " literal");
        }
    }

    /**
     * Parses a declared default with the same rules used by generated Java source.
     */
    public static Object parseDefaultValue(Class<?> type, String value) {
        Class<?> targetType = Objects.requireNonNull(type, "type");
        generateDefaultValueCode(targetType, value);
        if (value == null
                || (value.isBlank() && targetType != String.class && targetType != char.class
                && targetType != Character.class)) {
            return targetType.isPrimitive() ? Array.get(Array.newInstance(targetType, 1), 0) : null;
        }
        return transfer(value, getWrapperClass(targetType));
    }

    private static String getPrimitiveDefaultValue(Class<?> type, String value) {
        String literal = value.trim();
        try {
            if (type == short.class) {
                return "(short)" + Short.parseShort(literal);
            }
            if (type == int.class) {
                return Integer.toString(Integer.parseInt(literal));
            }
            if (type == long.class) {
                return Long.toString(Long.parseLong(literal)) + "L";
            }
            if (type == double.class) {
                double parsed = Double.parseDouble(literal);
                requireFinite(parsed, value);
                return Double.toString(parsed);
            }
            if (type == float.class) {
                float parsed = Float.parseFloat(literal);
                requireFinite(parsed, value);
                return Float.toString(parsed) + "F";
            }
            if (type == byte.class) {
                return "(byte)" + Byte.parseByte(literal);
            }
            if (type == char.class) {
                if (value.length() != 1) {
                    throw new IllegalArgumentException("A char default must contain one character");
                }
                return JavaSourceLiteral.characterLiteral(value.charAt(0));
            }
            if (type == boolean.class) {
                if ("true".equalsIgnoreCase(literal)) {
                    return "true";
                }
                if ("false".equalsIgnoreCase(literal)) {
                    return "false";
                }
                throw new IllegalArgumentException("Not a boolean literal");
            }
        } catch (IllegalArgumentException failure) {
            throw new DataTypeException.ConversionException("String", type.getName() + " literal");
        }
        throw new DataTypeException.UnsupportedTypeException(type.getName());
    }

    private static DefaultValueCode getObjectDefaultValue(Class<?> type, String value) {
        if (type == String.class) {
            return defaultValueCode(type, JavaSourceLiteral.stringExpression(value));
        }
        if (type == Short.class) {
            return defaultValueCode(type,
                    "java.lang.Short.valueOf(" + getPrimitiveDefaultValue(short.class, value) + ")");
        }
        if (type == Integer.class) {
            return defaultValueCode(type,
                    "java.lang.Integer.valueOf(" + getPrimitiveDefaultValue(int.class, value) + ")");
        }
        if (type == Long.class) {
            return defaultValueCode(type, "java.lang.Long.valueOf(" + getPrimitiveDefaultValue(long.class, value) + ")");
        }
        if (type == Double.class) {
            return defaultValueCode(type,
                    "java.lang.Double.valueOf(" + getPrimitiveDefaultValue(double.class, value) + ")");
        }
        if (type == Float.class) {
            return defaultValueCode(type,
                    "java.lang.Float.valueOf(" + getPrimitiveDefaultValue(float.class, value) + ")");
        }
        if (type == Byte.class) {
            return defaultValueCode(type, "java.lang.Byte.valueOf(" + getPrimitiveDefaultValue(byte.class, value) + ")");
        }
        if (type == Character.class) {
            return defaultValueCode(type,
                    "java.lang.Character.valueOf(" + getPrimitiveDefaultValue(char.class, value) + ")");
        }
        if (type == Boolean.class) {
            return defaultValueCode(type,
                    "java.lang.Boolean.valueOf(" + getPrimitiveDefaultValue(boolean.class, value) + ")");
        }
        if (type == BigDecimal.class) {
            try {
                String canonical = new BigDecimal(value.trim()).toString();
                return defaultValueCode(type, "new java.math.BigDecimal(\"" + canonical + "\")");
            } catch (NumberFormatException failure) {
                throw new DataTypeException.ConversionException("String", BigDecimal.class.getName() + " literal");
            }
        }
        if (type == BigInteger.class) {
            try {
                String canonical = new BigInteger(value.trim()).toString();
                return defaultValueCode(type, "new java.math.BigInteger(\"" + canonical + "\")");
            } catch (NumberFormatException failure) {
                throw new DataTypeException.ConversionException("String", BigInteger.class.getName() + " literal");
            }
        }
        if (type == LocalDate.class) {
            LocalDate parsed = parseLocalDate(value);
            return defaultValueCode(type, "java.time.LocalDate.parse(\"" + parsed + "\")");
        }
        if (type == LocalTime.class) {
            LocalTime parsed = parseLocalTime(value);
            return defaultValueCode(type, "java.time.LocalTime.parse(\"" + parsed + "\")");
        }
        if (type == LocalDateTime.class) {
            LocalDateTime parsed = parseLocalDateTime(value);
            return defaultValueCode(type, "java.time.LocalDateTime.parse(\"" + parsed + "\")");
        }
        if (type == Instant.class) {
            Instant parsed = parseInstant(value);
            return defaultValueCode(type, "java.time.Instant.parse(\"" + parsed + "\")");
        }
        if (type == java.sql.Date.class) {
            LocalDate parsed = parseLocalDate(value);
            return defaultValueCode(type, "java.sql.Date.valueOf(\"" + parsed + "\")");
        }
        if (type == java.sql.Time.class) {
            LocalTime parsed = parseLocalTime(value);
            if (parsed.getNano() != 0) {
                throw conversionFailure(value, java.sql.Time.class);
            }
            return defaultValueCode(type, "java.sql.Time.valueOf(\"" + java.sql.Time.valueOf(parsed) + "\")");
        }
        if (type == Timestamp.class) {
            return getTimestampDefaultValue(value);
        }
        if (type == java.util.Date.class) {
            Instant parsed = parseInstant(value);
            toUtilDateExact(parsed, value);
            return defaultValueCode(type, "new java.util.Date(" + parsed.toEpochMilli() + "L)");
        }
        throw new DataTypeException.UnsupportedTypeException(type.getName());
    }

    private static DefaultValueCode getTimestampDefaultValue(String value) {
        try {
            LocalDateTime parsed = parseLocalDateTime(value);
            return defaultValueCode(Timestamp.class, "java.sql.Timestamp.valueOf(\"" + Timestamp.valueOf(parsed) + "\")");
        } catch (DataTypeException.ConversionException localFailure) {
            Instant parsed = parseInstant(value);
            return defaultValueCode(Timestamp.class,
                    "java.sql.Timestamp.from(java.time.Instant.parse(\"" + parsed + "\"))", Instant.class);
        }
    }

    private static DefaultValueCode defaultValueCode(Class<?> targetType, String expression,
            Class<?>... additionalTypes) {
        List<Class<?>> referencedTypes = new ArrayList<>(1 + additionalTypes.length);
        if (!targetType.isPrimitive()) {
            referencedTypes.add(targetType);
        }
        for (Class<?> additionalType : additionalTypes) {
            referencedTypes.add(additionalType);
        }
        return new DefaultValueCode(expression, referencedTypes);
    }

    private static void requireFinite(double value, String source) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Non-finite numeric default: " + source);
        }
    }

    private static void requireFinite(float value, String source) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("Non-finite numeric default: " + source);
        }
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

    @SuppressWarnings("unchecked")
    public static <T> T transfer(Object value, Class<T> type) {
        Class<T> targetType = Objects.requireNonNull(type, "type");
        if (value == null) {
            return null;
        }

        if (targetType.isInstance(value)) {
            return (T) value;
        }

        ConversionType conversionType = TYPE_CONVERSION_MAP.get(targetType);
        if (conversionType != null) {
            try {
                return (T) conversionType.convert(value);
            } catch (DataTypeException failure) {
                throw failure;
            } catch (RuntimeException failure) {
                throw new DataTypeException.ConversionException(value.getClass().getName(), targetType.getName());
            }
        }

        throw new DataTypeException.ConversionException(value.getClass().getName(), targetType.getName());
    }

    private static Object safeShortValue(Object value) {
        try {
            long converted = toExactLong(value);
            if (converted < Short.MIN_VALUE || converted > Short.MAX_VALUE) {
                throw new ArithmeticException("short overflow");
            }
            return (short) converted;
        } catch (ArithmeticException | NumberFormatException failure) {
            throw conversionFailure(value, Short.class);
        }
    }

    // --------- Strict conversion helpers ---------
    private static Object safeIntValue(Object value) {
        try {
            long converted = toExactLong(value);
            if (converted < Integer.MIN_VALUE || converted > Integer.MAX_VALUE) {
                throw new ArithmeticException("integer overflow");
            }
            return (int) converted;
        } catch (ArithmeticException | NumberFormatException failure) {
            throw conversionFailure(value, Integer.class);
        }
    }

    private static Object safeLongValue(Object value) {
        try {
            return toExactLong(value);
        } catch (ArithmeticException | NumberFormatException failure) {
            throw conversionFailure(value, Long.class);
        }
    }

    private static Object safeByteValue(Object value) {
        try {
            long converted = toExactLong(value);
            if (converted < Byte.MIN_VALUE || converted > Byte.MAX_VALUE) {
                throw new ArithmeticException("byte overflow");
            }
            return (byte) converted;
        } catch (ArithmeticException | NumberFormatException failure) {
            throw conversionFailure(value, Byte.class);
        }
    }

    private static Object safeDoubleValue(Object value) {
        double converted;
        try {
            converted = value instanceof Number
                    ? ((Number) value).doubleValue()
                    : Double.parseDouble(requireText(value));
        } catch (NumberFormatException failure) {
            throw conversionFailure(value, Double.class);
        }
        if (!Double.isFinite(converted)) {
            throw conversionFailure(value, Double.class);
        }
        return converted;
    }

    private static Object safeFloatValue(Object value) {
        float converted;
        try {
            converted = value instanceof Number ? ((Number) value).floatValue() : Float.parseFloat(requireText(value));
        } catch (NumberFormatException failure) {
            throw conversionFailure(value, Float.class);
        }
        if (!Float.isFinite(converted)) {
            throw conversionFailure(value, Float.class);
        }
        return converted;
    }

    private static Object safeBigDecimalValue(Object value) {
        try {
            return toBigDecimal(value);
        } catch (NumberFormatException failure) {
            throw conversionFailure(value, BigDecimal.class);
        }
    }

    private static Object safeBigIntegerValue(Object value) {
        try {
            if (value instanceof BigInteger integer) {
                return integer;
            }
            if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
                return BigInteger.valueOf(((Number) value).longValue());
            }
            return toBigDecimal(value).toBigIntegerExact();
        } catch (ArithmeticException | NumberFormatException failure) {
            throw conversionFailure(value, BigInteger.class);
        }
    }

    private static long toExactLong(Object value) {
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return ((Number) value).longValue();
        }
        if (value instanceof BigInteger integer) {
            return integer.longValueExact();
        }
        return toBigDecimal(value).longValueExact();
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof BigInteger integer) {
            return new BigDecimal(integer);
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return BigDecimal.valueOf(((Number) value).longValue());
        }
        if (value instanceof Double number && !Double.isFinite(number)) {
            throw new NumberFormatException("Non-finite double");
        }
        if (value instanceof Float number && !Float.isFinite(number)) {
            throw new NumberFormatException("Non-finite float");
        }
        if (value instanceof Number || value instanceof CharSequence) {
            return new BigDecimal(requireText(value));
        }
        throw new NumberFormatException("Unsupported numeric source type");
    }

    private static Object safeBooleanValue(Object value) {
        if (value instanceof Boolean) {
            return value;
        }
        if (value instanceof CharSequence) {
            String text = requireText(value);
            if ("true".equalsIgnoreCase(text)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(text)) {
                return Boolean.FALSE;
            }
        }
        throw conversionFailure(value, Boolean.class);
    }

    private static Object safeCharValue(Object value) {
        if (value instanceof Character) {
            return value;
        }
        if (value instanceof CharSequence sequence && sequence.length() == 1) {
            return sequence.charAt(0);
        }
        throw conversionFailure(value, Character.class);
    }

    private static Object safeStringValue(Object value) {
        return value instanceof String ? value : String.valueOf(value);
    }

    private static Object safeLocalDateValue(Object value) {
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        return parseLocalDate(requireText(value));
    }

    private static Object safeLocalTimeValue(Object value) {
        if (value instanceof java.sql.Time time) {
            return time.toLocalTime();
        }
        return parseLocalTime(requireText(value));
    }

    private static Object safeLocalDateTimeValue(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return parseLocalDateTime(requireText(value));
    }

    private static Object safeInstantValue(Object value) {
        if (value instanceof java.util.Date date) {
            return date.toInstant();
        }
        return parseInstant(requireText(value));
    }

    private static Object safeSqlDateValue(Object value) {
        try {
            LocalDate date = value instanceof LocalDate localDate ? localDate : parseLocalDate(requireText(value));
            return java.sql.Date.valueOf(date);
        } catch (RuntimeException failure) {
            throw conversionFailure(value, java.sql.Date.class);
        }
    }

    private static Object safeSqlTimeValue(Object value) {
        try {
            LocalTime time = value instanceof LocalTime localTime ? localTime : parseLocalTime(requireText(value));
            if (time.getNano() != 0) {
                throw conversionFailure(value, java.sql.Time.class);
            }
            return java.sql.Time.valueOf(time);
        } catch (RuntimeException failure) {
            throw conversionFailure(value, java.sql.Time.class);
        }
    }

    private static Object safeSqlTimestampValue(Object value) {
        try {
            if (value instanceof LocalDateTime dateTime) {
                return Timestamp.valueOf(dateTime);
            }
            if (value instanceof Instant instant) {
                return Timestamp.from(instant);
            }
            if (value instanceof java.util.Date date) {
                return Timestamp.from(date.toInstant());
            }
            String text = requireText(value);
            try {
                return Timestamp.valueOf(parseLocalDateTime(text));
            } catch (DataTypeException.ConversionException localFailure) {
                return Timestamp.from(parseInstant(text));
            }
        } catch (RuntimeException failure) {
            throw conversionFailure(value, Timestamp.class);
        }
    }

    private static Object safeUtilDateValue(Object value) {
        try {
            Instant instant = value instanceof Instant source ? source : parseInstant(requireText(value));
            return toUtilDateExact(instant, value);
        } catch (RuntimeException failure) {
            throw conversionFailure(value, java.util.Date.class);
        }
    }

    private static java.util.Date toUtilDateExact(Instant instant, Object source) {
        try {
            java.util.Date converted = java.util.Date.from(instant);
            if (!converted.toInstant().equals(instant)) {
                throw conversionFailure(source, java.util.Date.class);
            }
            return converted;
        } catch (DataTypeException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw conversionFailure(source, java.util.Date.class);
        }
    }

    private static LocalDate parseLocalDate(String source) {
        try {
            return LocalDate.parse(source.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException failure) {
            throw conversionFailure(source, LocalDate.class);
        }
    }

    private static LocalTime parseLocalTime(String source) {
        try {
            return LocalTime.parse(source.trim(), TIME_FORMATTER);
        } catch (DateTimeParseException failure) {
            throw conversionFailure(source, LocalTime.class);
        }
    }

    private static LocalDateTime parseLocalDateTime(String source) {
        try {
            return LocalDateTime.parse(source.trim(), DATETIME_FORMATTER);
        } catch (DateTimeParseException failure) {
            throw conversionFailure(source, LocalDateTime.class);
        }
    }

    private static Instant parseInstant(String source) {
        try {
            return Instant.parse(source.trim());
        } catch (DateTimeParseException failure) {
            throw conversionFailure(source, Instant.class);
        }
    }

    private static String requireText(Object value) {
        if (!(value instanceof CharSequence) && !(value instanceof Number)) {
            throw new IllegalArgumentException("Value is not textual or numeric");
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Value is blank");
        }
        return text;
    }

    private static DataTypeException.ConversionException conversionFailure(Object value, Class<?> targetType) {
        return new DataTypeException.ConversionException(value == null ? "null" : value.getClass().getName(),
                targetType.getName());
    }

    // Type conversion strategy pattern for O(1) lookup
    private enum ConversionType {
        SHORT(Short.class, short.class, DataTypes::safeShortValue),
        INTEGER(Integer.class, int.class, DataTypes::safeIntValue),
        LONG(Long.class, long.class, DataTypes::safeLongValue),
        DOUBLE(Double.class, double.class, DataTypes::safeDoubleValue),
        FLOAT(Float.class, float.class, DataTypes::safeFloatValue),
        BYTE(Byte.class, byte.class, DataTypes::safeByteValue),
        BOOLEAN(Boolean.class, boolean.class, DataTypes::safeBooleanValue),
        CHARACTER(Character.class, char.class, DataTypes::safeCharValue),
        STRING(String.class, null, DataTypes::safeStringValue),
        SQL_DATE(java.sql.Date.class, null, DataTypes::safeSqlDateValue),
        SQL_TIME(java.sql.Time.class, null, DataTypes::safeSqlTimeValue),
        SQL_TIMESTAMP(Timestamp.class, null, DataTypes::safeSqlTimestampValue),
        UTIL_DATE(java.util.Date.class, null, DataTypes::safeUtilDateValue),
        LOCAL_DATE(LocalDate.class, null, DataTypes::safeLocalDateValue),
        LOCAL_TIME(LocalTime.class, null, DataTypes::safeLocalTimeValue),
        LOCAL_DATE_TIME(LocalDateTime.class, null, DataTypes::safeLocalDateTimeValue),
        INSTANT(Instant.class, null, DataTypes::safeInstantValue),
        BIG_DECIMAL(BigDecimal.class, null, DataTypes::safeBigDecimalValue),
        BIG_INTEGER(BigInteger.class, null, DataTypes::safeBigIntegerValue);
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

    /**
     * One validated Java default-value expression with fully qualified type references.
     *
     * @param expression      Java source expression
     * @param referencedTypes types available for collision-aware import shortening
     */
    public record DefaultValueCode(String expression, List<Class<?>> referencedTypes) {
        public DefaultValueCode {
            expression = Objects.requireNonNull(expression, "expression");
            referencedTypes = List.copyOf(Objects.requireNonNull(referencedTypes, "referencedTypes"));
        }
    }
}
