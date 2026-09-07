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
package com.alibaba.compileflow.durable.spring.boot.autoconfigure.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ResourceLoader;

class PostgresDurableProviderConditionTest {
    private static final String MYSQL_AUTO_CONFIGURATION =
            "com.alibaba.compileflow.durable.spring.boot.autoconfigure.mysql.CompileFlowDurableMySqlAutoConfiguration";

    @Test
    void implicitSelectionRequiresPostgresToBeTheOnlyAvailableProvider() {
        assertThat(matches(null, hiding(MYSQL_AUTO_CONFIGURATION))).isTrue();
        assertThat(matches(null, getClass().getClassLoader())).isFalse();
    }

    @Test
    void explicitSelectionOverridesCoexistingProviderAvailability() {
        assertThat(matches("POSTGRESQL", getClass().getClassLoader())).isTrue();
        assertThat(matches("mysql", getClass().getClassLoader())).isFalse();
        assertThat(matches("unsupported", getClass().getClassLoader())).isFalse();
    }

    private boolean matches(String selector, ClassLoader classLoader) {
        StandardEnvironment environment = new StandardEnvironment();
        if (selector != null) {
            environment
                .getPropertySources()
                .addFirst(new MapPropertySource("test", Map.of("compileflow.durable.database.provider", selector)));
        }
        return new PostgresDurableProviderCondition().matches(new TestConditionContext(environment, classLoader), null);
    }

    private ClassLoader hiding(String className) {
        ClassLoader parent = getClass().getClassLoader();
        return new ClassLoader(parent) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (className.equals(name)) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        };
    }

    private record TestConditionContext(StandardEnvironment environment, ClassLoader classLoader)
            implements ConditionContext {
        @Override
        public BeanDefinitionRegistry getRegistry() {
            return null;
        }

        @Override
        public ConfigurableListableBeanFactory getBeanFactory() {
            return null;
        }

        @Override
        public StandardEnvironment getEnvironment() {
            return environment;
        }

        @Override
        public ResourceLoader getResourceLoader() {
            return null;
        }

        @Override
        public ClassLoader getClassLoader() {
            return classLoader;
        }
    }
}
