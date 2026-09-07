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
package com.alibaba.compileflow.engine.spring.boot.autoconfigure.resolution;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.spi.ProcessComponentResolver;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;

/**
 * Resolves process components as Spring beans by name.
 *
 * @author yusu
 */
public final class SpringProcessComponentResolver implements ProcessComponentResolver {
    private final ApplicationContext applicationContext;
    private final Set<String> allowedBeanNames;

    /**
     * Creates a resolver backed by one application context.
     *
     * @param applicationContext non-null Spring application context
     * @param allowedBeanNames   exact bean names exposed to process definitions
     */
    public SpringProcessComponentResolver(ApplicationContext applicationContext, Collection<String> allowedBeanNames) {
        this.applicationContext = Objects.requireNonNull(applicationContext, "applicationContext");
        this.allowedBeanNames = Set.copyOf(Objects.requireNonNull(allowedBeanNames, "allowedBeanNames"));
        for (String beanName : this.allowedBeanNames) {
            if (!applicationContext.containsBean(beanName)) {
                throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_003,
                        "Allowed process component is not a Spring bean: " + beanName);
            }
        }
    }

    @Override
    public <T> T resolve(String name, Class<T> requiredType) {
        Objects.requireNonNull(name, "component name must not be null");
        Objects.requireNonNull(requiredType, "required component type must not be null");
        if (!allowedBeanNames.contains(name)) {
            throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_003,
                    "Process component is not available: " + name);
        }
        try {
            return applicationContext.getBean(name, requiredType);
        } catch (BeansException failure) {
            throw new CompileFlowException.ConfigurationException(ErrorCode.CF_CONFIG_003,
                    "Process component '" + name + "' is not available as " + requiredType.getName(), failure);
        }
    }
}
