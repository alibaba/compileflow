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
package com.alibaba.compileflow.deploy.spring.boot.autoconfigure.postgres;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Selects PostgreSQL implicitly when it is the only provider, or explicitly when providers coexist.
 */
final class PostgresDeployProviderCondition implements Condition {
    private static final String SELECTOR = "compileflow.deploy.database.provider";
    private static final String MYSQL_AUTO_CONFIGURATION =
            "com.alibaba.compileflow.deploy.spring.boot.autoconfigure.mysql.CompileFlowDeployMySqlAutoConfiguration";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String selected = context.getEnvironment().getProperty(SELECTOR);
        if (selected != null && !selected.isBlank()) {
            return "POSTGRESQL".equalsIgnoreCase(selected);
        }
        return !isPresent(MYSQL_AUTO_CONFIGURATION, context.getClassLoader());
    }

    private static boolean isPresent(String className, ClassLoader classLoader) {
        try {
            Class.forName(className, false, classLoader);
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }
}
