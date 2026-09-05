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
package com.alibaba.compileflow.engine.core.java.codegen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import org.junit.jupiter.api.Test;

class JavaTypeNameTest {
    @Test
    void shortensEveryNestedGenericTypeAndTracksItsImports() {
        JavaTypeName type = JavaTypeName.of("java.util.List<java.util.Map<java.lang.String, java.lang.Integer>>[]");

        assertThat(type.getShortName()).isEqualTo("List<Map<String, Integer>>[]");
        assertThat(type.getShortRawName()).isEqualTo("List[]");
        assertThat(type.getSimpleName()).isEqualTo("List");
        assertThat(type.getPackageName()).isEqualTo("java.util");
        assertThat(type.getImportName()).isEqualTo("java.util.List");
        assertThat(type.getReferencedTypes())
            .extracting(JavaTypeName::getImportName)
            .containsExactly("java.util.List", "java.util.Map", "java.lang.String", "java.lang.Integer");
    }

    @Test
    void shortensWildcardsMemberTypesAndArrays() {
        JavaTypeName type = JavaTypeName.of("java.util.Map.Entry<java.lang.String, ? extends java.lang.Number[]>[]");

        assertThat(type.getShortName()).isEqualTo("Entry<String, ? extends Number[]>[]");
        assertThat(type.getShortRawName()).isEqualTo("Entry[]");
        assertThat(type.getReferencedTypes())
            .extracting(JavaTypeName::getImportName)
            .containsExactly("java.util.Map.Entry", "java.lang.String", "java.lang.Number");
    }

    @Test
    void usesCanonicalSourceNamesForClassArrays() {
        JavaTypeName type = JavaTypeName.of(String[][].class);

        assertThat(type.getName()).isEqualTo("java.lang.String[][]");
        assertThat(type.getShortName()).isEqualTo("String[][]");
        assertThat(type.getShortRawName()).isEqualTo("String[][]");
        assertThat(type.getImportName()).isEqualTo("java.lang.String");
    }

    @Test
    void primitiveTypesHaveNoImport() {
        JavaTypeName type = JavaTypeName.of("int");

        assertThat(type.getShortName()).isEqualTo("int");
        assertThat(type.getPackageName()).isNull();
        assertThat(type.getImportName()).isNull();
    }

    @Test
    void rejectsMissingNames() {
        assertThatNullPointerException()
            .isThrownBy(() -> JavaTypeName.of((String) null))
            .withMessage("name");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> JavaTypeName.of("  "))
            .withMessage("class name must not be blank");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> JavaTypeName.of(" java.lang.String "))
            .withMessageContaining("surrounding whitespace");
    }
}
