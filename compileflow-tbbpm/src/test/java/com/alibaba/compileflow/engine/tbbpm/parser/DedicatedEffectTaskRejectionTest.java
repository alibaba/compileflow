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
package com.alibaba.compileflow.engine.tbbpm.parser;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DedicatedEffectTaskRejectionTest {
    @Test
    void dedicatedEffectTaskIsNotPartOfTheV2Model() {
        String xml =
                """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="effect.contract" name="Effect Contract">
                <start id="start" name="Start" g="0,0,32,32">
                    <transition to="createCoupon"/>
                </start>
                <effectTask id="createCoupon" name="Create coupon"
                            g="60,0,100,40" effect="coupon.create"
                            effectVersion="1">
                    <transition to="end"/>
                </effectTask>
                <end id="end" name="End" g="200,0,32,32"/>
            </bpm>
            """;

        assertThatThrownBy(() -> TbbpmXmlParser
            .getInstance()
            .parse(FlowSource.of("effect.contract", xml.getBytes(StandardCharsets.UTF_8))))
            .isInstanceOf(RuntimeException.class);
    }
}
