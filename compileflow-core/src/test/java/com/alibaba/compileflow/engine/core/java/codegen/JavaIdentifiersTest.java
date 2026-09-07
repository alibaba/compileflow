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
import com.alibaba.compileflow.engine.core.java.naming.JavaNames;
import com.alibaba.compileflow.engine.core.semantic.naming.ProcessNames;
import org.junit.jupiter.api.Test;

class JavaIdentifiersTest {
    @Test
    void usesStableJava17KeywordRules() {
        assertThat(JavaIdentifiers.isJavaIdentifier("customerId")).isTrue();
        assertThat(JavaIdentifiers.isJavaIdentifier("record")).isFalse();
        assertThat(JavaIdentifiers.isJavaIdentifier("sealed")).isFalse();
        assertThat(JavaIdentifiers.isJavaIdentifier("permits")).isFalse();
        assertThat(JavaIdentifiers.isJavaIdentifier("var")).isFalse();
        assertThat(JavaIdentifiers.isJavaIdentifier("yield")).isFalse();
        assertThat(JavaIdentifiers.toJavaIdentifier("record")).isEqualTo("_record");
    }

    @Test
    void rejectsInvisibleIgnorableCharactersInGeneratedIdentifiers() {
        String visuallyAmbiguous = "customer\u200Cid";

        assertThat(Character.isJavaIdentifierPart(0x200C)).isTrue();
        assertThat(Character.isIdentifierIgnorable(0x200C)).isTrue();
        assertThat(JavaIdentifiers.isJavaIdentifier(visuallyAmbiguous)).isFalse();
        assertThat(JavaNames.isClassName("example." + visuallyAmbiguous)).isFalse();
        assertThat(JavaIdentifiers.toJavaIdentifier(visuallyAmbiguous)).isEqualTo("customer_id");
    }

    @Test
    void stableMethodSuffixPreservesIdentifiersThatReadableNormalizationCollapses() {
        assertThat(JavaIdentifiers.toMethodSuffix("a-b")).isEqualTo(JavaIdentifiers.toMethodSuffix("a_b"));

        String hyphenated = JavaIdentifiers.toStableMethodSuffix("a-b");
        String underscored = JavaIdentifiers.toStableMethodSuffix("a_b");

        assertThat(hyphenated)
            .startsWith("N")
            .hasSize(65)
            .isEqualTo(JavaIdentifiers.toStableMethodSuffix("a-b"))
            .isNotEqualTo(underscored);
        assertThat(JavaIdentifiers.isJavaIdentifier(hyphenated)).isTrue();
        assertThat(JavaIdentifiers.isJavaIdentifier(underscored)).isTrue();
    }

    @Test
    void recognizesGeneratedAndMetadataNamespacesAsEngineOwned() {
        assertThat(ProcessNames.isReserved("_cf$nodeId")).isTrue();
        assertThat(ProcessNames.isReserved("__cf_effect_id")).isTrue();
        assertThat(ProcessNames.isReserved("customerId")).isFalse();
    }
}
