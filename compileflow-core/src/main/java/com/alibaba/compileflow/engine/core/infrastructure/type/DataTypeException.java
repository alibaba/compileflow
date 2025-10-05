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
package com.alibaba.compileflow.engine.core.infrastructure.type;

/**
 * Exception for data type related operations.
 *
 * @author yusu
 */
public class DataTypeException extends RuntimeException {

    public DataTypeException(String message) {
        super(message);
    }

    public DataTypeException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Exception for type conversion failures.
     */
    public static class ConversionException extends DataTypeException {
        public ConversionException(String fromType, String toType, Object value, Throwable cause) {
            super(String.format("Failed to convert value '%s' from type '%s' to type '%s'",
                    value, fromType, toType), cause);
        }

        public ConversionException(String fromType, String toType, Object value) {
            super(String.format("Failed to convert value '%s' from type '%s' to type '%s'",
                    value, fromType, toType));
        }
    }

    /**
     * Exception for unsupported type operations.
     */
    public static class UnsupportedTypeException extends DataTypeException {
        public UnsupportedTypeException(String typeName) {
            super(String.format("Unsupported data type: '%s'", typeName));
        }
    }

    /**
     * Exception for invalid macro values.
     */
    public static class InvalidMacroException extends DataTypeException {
        public InvalidMacroException(String macroValue) {
            super(String.format("Invalid macro value: '%s'", macroValue));
        }
    }

}
