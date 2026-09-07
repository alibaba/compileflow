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
package com.alibaba.compileflow.durable.api.effect;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

class EffectReconcileOutcomeTest {
    @Test
    void preservesTheOriginalConcreteBusinessResultType() {
        var list = new ArrayList<String>();
        list.add("confirmed");
        var map = new LinkedHashMap<String, String>();
        map.put("result", "confirmed");
        var set = EnumSet.of(EffectRecoveryPlan.Mode.RECONCILE);

        ArrayList<String> confirmedList = new EffectReconcileOutcome.ConfirmedResult<>(list).value();
        LinkedHashMap<String, String> confirmedMap = new EffectReconcileOutcome.ConfirmedResult<>(map).value();
        EnumSet<EffectRecoveryPlan.Mode> confirmedSet = new EffectReconcileOutcome.ConfirmedResult<>(set).value();

        assertThat(confirmedList).isSameAs(list);
        assertThat(confirmedMap).isSameAs(map);
        assertThat(confirmedSet).isSameAs(set);
    }

    @Test
    void supportsVoidResultsWithoutExposingBusinessValuesInDiagnostics() {
        assertThat(new EffectReconcileOutcome.ConfirmedResult<Void>(null).value()).isNull();
        assertThat(EffectReconcileOutcome.confirmed("business-secret").toString())
            .isEqualTo("ConfirmedResult[value=<redacted>]");
    }
}
