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
package com.alibaba.compileflow.engine.core.java.expression;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JavaExpressionInspectorTest {
    @Test
    void extractsOnlyProcessStateReadsFromJavaCode() {
        assertThat(JavaExpressionInspector.referencedIdentifiers("""
                this.amount > order.amount
                    && status.equals("amount")
                    && helper(status)
                    // ignored
                    && note != null
                """,
                Set.of("amount", "order", "status", "helper", "note")))
            .containsExactly("amount", "order", "status", "note");
    }

    @Test
    void recognizesParenthesizedThisFieldsAndSupplementaryIdentifiers() {
        String supplementaryIdentifier = "\uD801\uDC00value";

        assertThat(JavaExpressionInspector.referencedIdentifiers("(this).shared != null"
                + " && ((( /* receiver */ this ))).status != null" + " && this /* receiver */ . shared != null"
                + " && holder.shared != null" + " && holder /* receiver */ . status != null"
                + " && helper /* invocation */ (status)" + " && " + supplementaryIdentifier + " != null",
                Set.of("shared", "status", "holder", "helper", supplementaryIdentifier)))
            .containsExactly("shared", "status", "holder", supplementaryIdentifier);

        assertThat(JavaExpressionInspector.referencedIdentifiers("this // receiver\n . shared != null", Set.of("shared")))
            .containsExactly("shared");
    }

    @Test
    void acceptsComparisonsAndOperatorsWithoutMutation() {
        assertThat(JavaExpressionInspector.findDirectMutation(
                "amount >= 1 && amount <= 10 && status != null" + " && value == expected && flags.stream()"
                + ".anyMatch(flag -> flag == true)"))
            .isNull();
    }

    @Test
    void ignoresMutationSyntaxInsideLiteralsAndComments() {
        assertThat(JavaExpressionInspector.findDirectMutation(
                "\"value++\".equals(text) /* total = 1 */" + " && marker == '=' // count--\n" + " && active"))
            .isNull();
    }

    @Test
    void rejectsEveryDirectMutationFamilyAndUnicodeEscapes() {
        assertThat(JavaExpressionInspector.findDirectMutation("enabled = true")).isEqualTo("=");
        assertThat(JavaExpressionInspector.findDirectMutation("count++ > 0")).isEqualTo("++");
        assertThat(JavaExpressionInspector.findDirectMutation("--remaining > 0")).isEqualTo("--");
        assertThat(JavaExpressionInspector.findDirectMutation("(flags |= mask) != 0")).isEqualTo("|=");
        assertThat(JavaExpressionInspector.findDirectMutation("(bits >>>= 1) > 0")).isEqualTo(">>>=");
        assertThat(JavaExpressionInspector.findDirectMutation("enabled \\u003d true")).isEqualTo("unicode escape");
    }
}
