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
package com.alibaba.compileflow.engine.core.xml.parser;

import com.alibaba.compileflow.engine.ProcessIdentifiers;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Objects;

/**
 * Immutable, repeatable snapshot of one flow definition.
 *
 * @author yusu
 */
public final class FlowSource {
    private final String code;
    private final byte[] content;

    private FlowSource(String code, byte[] content) {
        this.code = ProcessIdentifiers.requireCode(code);
        this.content = Objects.requireNonNull(content, "content").clone();
    }

    public static FlowSource of(String code, byte[] content) {
        return new FlowSource(code, content);
    }

    public String getCode() {
        return code;
    }

    public InputStream openStream() {
        return new ByteArrayInputStream(content);
    }

    public int size() {
        return content.length;
    }
}
