/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements...
 */
package com.alibaba.compileflow.engine.core.infrastructure.type;

/**
 * Interface for type converters to enable extensibility.
 * Similar to Spring's Converter interface but simplified for CompileFlow needs.
 *
 * @param <S> Source type
 * @param <T> Target type
 * @author yusu
 */
@FunctionalInterface
public interface TypeConverter<S, T> {

    /**
     * Convert the source object of type S to target type T.
     *
     * @param source the source object to convert (may be null)
     * @return the converted object (may be null)
     * @throws DataTypeException.ConversionException if conversion fails
     */
    T convert(S source) throws DataTypeException.ConversionException;

    /**
     * Check if this converter can handle the given source and target types.
     * Default implementation returns true, subclasses can override for more specific checks.
     */
    default boolean canConvert(Class<?> sourceType, Class<?> targetType) {
        return true;
    }
}
