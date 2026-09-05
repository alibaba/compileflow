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
package com.alibaba.compileflow.engine.spring.boot.autoconfigure.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.List;

/**
 * Exact Spring bean exposure policy for generated process actions.
 *
 * @author yusu
 */
public final class EngineComponentProperties {
    /**
     * Spring bean names exposed to process definitions; empty disables automatic exposure.
     */
    @NotNull
    private final List<String> allowedBeans;

    public EngineComponentProperties(List<String> allowedBeans) {
        this.allowedBeans = allowedBeans == null ? List.of() : List.copyOf(allowedBeans);
    }

    @AssertTrue(message = "compileflow.engine.components.allowed-beans must contain unique, exact, non-blank bean names")
    public boolean isAllowedBeansValid() {
        HashSet<String> unique = new HashSet<>();
        for (String bean : allowedBeans) {
            if (bean == null || bean.isBlank() || !bean.equals(bean.trim()) || bean.startsWith("&") || !unique.add(bean)) {
                return false;
            }
        }
        return true;
    }

    public List<String> getAllowedBeans() {
        return allowedBeans;
    }

    @Override
    public String toString() {
        return "EngineComponentProperties{allowedBeanCount=" + allowedBeans.size() + '}';
    }
}
