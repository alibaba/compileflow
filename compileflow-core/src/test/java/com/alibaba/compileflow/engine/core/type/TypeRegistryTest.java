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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TypeRegistryTest {
    private static String defaultExpression(Class<?> type, String value) {
        return DataTypes.generateDefaultValueCode(type, value).expression();
    }

    @Test
    void resolvesOnlyExactBuiltInJavaTypeNames() {
        Map
            .of(short.class, Short.class, int.class, Integer.class, long.class, Long.class, double.class, Double.class,
                    float.class, Float.class, byte.class, Byte.class, char.class, Character.class, boolean.class,
                    Boolean.class)
            .forEach((primitive, wrapper) -> {
                assertThat(TypeRegistry.getJavaClass(primitive.getName())).isEqualTo(primitive);
                assertThat(TypeRegistry.getJavaClass(wrapper.getSimpleName())).isEqualTo(wrapper);
                assertThat(TypeRegistry.getJavaClass(wrapper.getName())).isEqualTo(wrapper);
            });
        assertThat(TypeRegistry.getJavaClass("String")).isEqualTo(String.class);
        assertThat(TypeRegistry.getJavaClass("Object")).isEqualTo(Object.class);
        assertThat(TypeRegistry.getJavaClass("void")).isNull();

        assertThat(TypeRegistry.getJavaClass("integer")).isNull();
        assertThat(TypeRegistry.getJavaClass("datetime")).isNull();
        assertThat(TypeRegistry.getJavaClass("bigdecimal")).isNull();
    }

    @Test
    void dataTypeRejectsCaseMismatchedClassNames() {
        assertThatThrownBy(() -> DataTypes.getJavaClass("integer"))
            .isInstanceOf(DataTypeException.UnsupportedTypeException.class);
    }

    @Test
    void primitiveObjectNormalizationUsesTheCanonicalWrapperName() {
        assertThat(DataTypes.normalizeToObjectTypeName("int")).isEqualTo("java.lang.Integer");
        assertThat(DataTypes.normalizeToObjectTypeName("Integer")).isEqualTo("java.lang.Integer");
        assertThat(DataTypes.normalizeToObjectTypeName(" int ")).isEqualTo(" int ");
        assertThat(DataTypes.normalizeToObjectTypeName("String")).isEqualTo("String");
        assertThat(DataTypes.normalizeToObjectTypeName("Object")).isEqualTo("Object");
    }

    @Test
    void unsupportedConversionsFailInsteadOfReturningTheWrongType() {
        Object value = new Object();

        assertThatThrownBy(() -> DataTypes.transfer(value, StringBuilder.class))
            .isInstanceOf(DataTypeException.ConversionException.class)
            .hasMessageContaining("java.lang.Object")
            .hasMessageContaining("java.lang.StringBuilder");
        assertThatThrownBy(() -> DataTypes.transfer(value, null)).isInstanceOf(NullPointerException.class).hasMessage(
                "type");
    }

    @Test
    void defaultValuesAreParsedBeforeGeneratingJavaLiterals() {
        assertThat(defaultExpression(long.class, "42")).isEqualTo("42L");
        assertThat(defaultExpression(Float.class, "1.5")).isEqualTo("java.lang.Float.valueOf(1.5F)");
        assertThat(defaultExpression(BigDecimal.class, "1.50")).isEqualTo("new java.math.BigDecimal(\"1.50\")");
        assertThat(defaultExpression(String.class, "@constants.DEFAULT_LIMIT")).isEqualTo(
                "\"@constants.DEFAULT_LIMIT\"");
        assertThat(defaultExpression(String.class, "")).isEqualTo("\"\"");
        assertThat(DataTypes.parseDefaultValue(String.class, "@constants.DEFAULT_LIMIT")).isEqualTo(
                "@constants.DEFAULT_LIMIT");
        assertThat(DataTypes.parseDefaultValue(Integer.class, "42")).isEqualTo(42);
        assertThat(DataTypes.parseDefaultValue(Integer.class, " ")).isNull();
        assertThat(DataTypes.parseDefaultValue(int.class, " ")).isEqualTo(0);
    }

    @Test
    void invalidOrUnsupportedDefaultsFailFast() {
        assertThatThrownBy(() -> DataTypes.generateDefaultValueCode(int.class, "@constants.DEFAULT_LIMIT"))
            .isInstanceOf(DataTypeException.ConversionException.class);
        assertThatThrownBy(() -> DataTypes.generateDefaultValueCode(int.class, "1; System.exit(0)"))
            .isInstanceOf(DataTypeException.ConversionException.class);
        assertThatThrownBy(() -> DataTypes.generateDefaultValueCode(char.class, "ab"))
            .isInstanceOf(DataTypeException.ConversionException.class);
        assertThatThrownBy(() -> DataTypes.generateDefaultValueCode(double.class, "NaN"))
            .isInstanceOf(DataTypeException.ConversionException.class);
        assertThatThrownBy(() -> DataTypes.generateDefaultValueCode(StringBuilder.class, "value"))
            .isInstanceOf(DataTypeException.UnsupportedTypeException.class);
    }
}
