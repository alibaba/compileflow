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
package com.alibaba.compileflow.durable.runtime.program;

/**
 * Pure replayable Actions shared by the Process/Durable differential corpus.
 *
 * @author yusu
 */
public final class TbbpmDifferentialActions {
    public Integer increment(Integer value) {
        return value + 1;
    }

    public String append(String path, String item, Integer index) {
        return path + item + index;
    }

    public String identity(String value) {
        return value;
    }

    public String label(String value, String label) {
        return value + '-' + label;
    }
}
