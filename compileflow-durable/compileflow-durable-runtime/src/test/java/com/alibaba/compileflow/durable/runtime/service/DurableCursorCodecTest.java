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
package com.alibaba.compileflow.durable.runtime.service;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import com.alibaba.compileflow.durable.api.model.ProcessTimelineCursor;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class DurableCursorCodecTest {
    @Test
    void rejectsMalformedUtf8() {
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[] {(byte) 0xc3, 0x28});

        assertThatIllegalArgumentException()
            .isThrownBy(() -> DurableCursorCodec.timeline(new ProcessTimelineCursor(token)))
            .withMessage("Invalid timeline cursor");
    }
}
