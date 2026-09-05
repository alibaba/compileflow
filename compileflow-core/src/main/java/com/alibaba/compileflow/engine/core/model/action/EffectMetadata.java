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
package com.alibaba.compileflow.engine.core.model.action;

/**
 * Source-syntax binding for Kernel metadata supplied to Durable Effect invocations.
 *
 * <p>This name is recognized only while normalizing an authoring model. Semantic plans use a
 * typed input source and do not retain this sentinel.
 *
 * @author yusu
 */
public final class EffectMetadata {
    public static final String ID = "__cf_effect_id";

    private EffectMetadata() {
    }

    /**
     * Returns whether a mapping source names Kernel-owned Effect metadata.
     */
    public static boolean isSource(String source) {
        return ID.equals(source);
    }

    /**
     * Returns whether a source attempts to address the reserved Effect metadata namespace.
     */
    public static boolean isReservedSource(String source) {
        return source != null && source.startsWith("__cf_effect_");
    }
}
