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

import java.util.Locale;

/**
 * Exception thrown when process data type conversion fails.
 *
 * @author yusu
 */
public class DataTypeException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DataTypeException(String message) {
        super(message);
    }

    public static class ConversionException extends DataTypeException {
        private static final long serialVersionUID = 1L;

        public ConversionException(String fromType, String toType) {
            super(String.format(Locale.ROOT, "Failed to convert value from type '%s' to type '%s'", fromType, toType));
        }
    }

    public static class UnsupportedTypeException extends DataTypeException {
        private static final long serialVersionUID = 1L;

        public UnsupportedTypeException(String typeName) {
            super(String.format(Locale.ROOT, "Unsupported data type: '%s'", typeName));
        }

        public UnsupportedTypeException(String typeName, String reason) {
            super(String.format(Locale.ROOT, "Unsupported data type '%s': %s", typeName, reason));
        }
    }
}
