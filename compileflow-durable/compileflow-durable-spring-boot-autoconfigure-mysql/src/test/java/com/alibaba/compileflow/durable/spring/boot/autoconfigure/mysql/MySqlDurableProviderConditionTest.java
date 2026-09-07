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
package com.alibaba.compileflow.durable.spring.boot.autoconfigure.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ResourceLoader;

class MySqlDurableProviderConditionTest {
    @Test
    void mysqlIsImplicitlySelectedWhenItIsTheOnlyAvailableProvider() {
        assertThat(matches(null)).isTrue();
    }

    @Test
    void explicitSelectionMustNameMysql() {
        assertThat(matches("MYSQL")).isTrue();
        assertThat(matches("postgresql")).isFalse();
        assertThat(matches("unsupported")).isFalse();
    }

    private boolean matches(String selector) {
        StandardEnvironment environment = new StandardEnvironment();
        if (selector != null) {
            environment
                .getPropertySources()
                .addFirst(new MapPropertySource("test", Map.of("compileflow.durable.database.provider", selector)));
        }
        return new MySqlDurableProviderCondition()
            .matches(new TestConditionContext(environment, getClass().getClassLoader()), null);
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
