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
import java.util.Map;
import org.junit.jupiter.api.Test;

class JavaTypeParserTest {
    private static void assertUnsupported(String type) {
        assertThatThrownBy(() -> DataTypes.getJavaClass(type)).isInstanceOf(
                DataTypeException.UnsupportedTypeException.class);
    }

    @Test
    void resolvesValidatedNestedGenericArrayTypes() {
        Class<?> resolved =
                DataTypes.getJavaClass("java.util.Map<java.lang.String, java.util.List<java.lang.Integer[]>> [] []");

        assertThat(resolved).isEqualTo(Map[][].class);
        assertThat(DataTypes.getTypeArguments(
                "java.util.Map<java.lang.String, java.util.List<java.lang.Integer[]>> [] []"))
            .containsExactly("java.lang.String", "java.util.List<java.lang.Integer[]>");
    }

    @Test
    void resolvesReferenceArraysAndCanonicalMemberClasses() {
        assertThat(DataTypes.getJavaClass("java.util.List<int[]>")).isEqualTo(java.util.List.class);
        assertThat(DataTypes.getJavaClass("java.util.List<? extends int[]>")).isEqualTo(java.util.List.class);
        assertThat(DataTypes.getJavaClass("java.util.Map.Entry<java.lang.String, java.lang.Integer>"))
            .isEqualTo(Map.Entry.class);
    }

    @Test
    void rejectsMalformedOrSemanticallyInvalidTypeDeclarations() {
        assertUnsupported("java.lang.String[] trailing");
        assertUnsupported("java.util.List<java.lang.String");
        assertUnsupported("java.util.List<int>");
        assertUnsupported("java.lang.String<java.lang.Integer>");
        assertUnsupported("java.util.Map<java.lang.String>");
        assertUnsupported("java.util.List<missing.Type>");
        assertUnsupported("int.value");
    }

    @Test
    void enforcesTheJvmArrayDimensionLimit() {
        String type = "java.lang.String" + "[]".repeat(256);

        assertUnsupported(type);
    }

    @Test
    void parsesIdentifiersByUnicodeCodePointAndRejectsInvisibleCharacters() {
        String supplementaryLetter = new String(Character.toChars(0x10400));
        String className = "example." + supplementaryLetter + "Type";

        assertThat(JavaTypeParser.parse(className).rawName()).isEqualTo(className);
        assertThat(JavaTypeParser.parse("example.module.with.Type").rawName()).isEqualTo("example.module.with.Type");
        assertUnsupported("example.Customer\u200CType");
    }
}
