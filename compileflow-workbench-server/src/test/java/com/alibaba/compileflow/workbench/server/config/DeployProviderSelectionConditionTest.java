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
package com.alibaba.compileflow.workbench.server.config;

import static org.assertj.core.api.Assertions.assertThat;
import java.lang.reflect.Constructor;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ResourceLoader;

/**
 * Proves the complete Deploy Provider selection truth table with both Providers on the product classpath.
 */
class DeployProviderSelectionConditionTest {
    private static final String POSTGRES_CONDITION =
            "com.alibaba.compileflow.deploy.spring.boot.autoconfigure." + "postgres.PostgresDeployProviderCondition";
    private static final String MYSQL_CONDITION =
            "com.alibaba.compileflow.deploy.spring.boot.autoconfigure." + "mysql.MySqlDeployProviderCondition";
    private static final String POSTGRES_AUTO_CONFIGURATION =
            "com.alibaba.compileflow.deploy.spring.boot.autoconfigure.postgres."
            + "CompileFlowDeployPostgresAutoConfiguration";
    private static final String MYSQL_AUTO_CONFIGURATION =
            "com.alibaba.compileflow.deploy.spring.boot.autoconfigure.mysql.CompileFlowDeployMySqlAutoConfiguration";

    @Test
    void oneAvailableProviderIsSelectedWithoutASelector() throws Exception {
        assertThat(matches(POSTGRES_CONDITION, null, hiding(MYSQL_AUTO_CONFIGURATION))).isTrue();
        assertThat(matches(MYSQL_CONDITION, null, hiding(POSTGRES_AUTO_CONFIGURATION))).isTrue();
    }

    @Test
    void bothAvailableProvidersRequireAnExplicitSelector() throws Exception {
        ClassLoader allProviders = getClass().getClassLoader();
        assertThat(matches(POSTGRES_CONDITION, null, allProviders)).isFalse();
        assertThat(matches(MYSQL_CONDITION, null, allProviders)).isFalse();
    }

    @Test
    void explicitSelectorActivatesExactlyOneAvailableProvider() throws Exception {
        ClassLoader allProviders = getClass().getClassLoader();
        assertThat(matches(POSTGRES_CONDITION, "POSTGRESQL", allProviders)).isTrue();
        assertThat(matches(MYSQL_CONDITION, "POSTGRESQL", allProviders)).isFalse();
        assertThat(matches(POSTGRES_CONDITION, "mysql", allProviders)).isFalse();
        assertThat(matches(MYSQL_CONDITION, "mysql", allProviders)).isTrue();
    }

    @Test
    void unsupportedSelectorActivatesNoProviderAndLeavesFailClosedAdmissionInCharge() throws Exception {
        ClassLoader allProviders = getClass().getClassLoader();
        assertThat(matches(POSTGRES_CONDITION, "unsupported", allProviders)).isFalse();
        assertThat(matches(MYSQL_CONDITION, "unsupported", allProviders)).isFalse();
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

    private static boolean matches(String conditionClass, String selector, ClassLoader classLoader) throws Exception {
        Constructor<?> constructor = Class.forName(conditionClass).getDeclaredConstructor();
        constructor.setAccessible(true);
        Condition condition = (Condition) constructor.newInstance();
        StandardEnvironment environment = new StandardEnvironment();
        if (selector != null) {
            environment
                .getPropertySources()
                .addFirst(new MapPropertySource("test", Map.of("compileflow.deploy.database.provider", selector)));
        }
        return condition.matches(new TestConditionContext(environment, classLoader), null);
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
