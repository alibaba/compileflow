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
package com.alibaba.compileflow.engine.core.semantic.plan;

import static com.alibaba.compileflow.engine.core.semantic.SemanticText.requireExpression;
import static com.alibaba.compileflow.engine.core.semantic.SemanticText.requireIdentity;
import com.alibaba.compileflow.engine.core.java.naming.JavaNames;
import com.alibaba.compileflow.engine.spi.script.ScriptExecutor;

/**
 * Immutable description of how an Action or reconcile adapter reaches its implementation.
 *
 * <p>This is deliberately independent of the mutable source-format {@code Action} model.
 *
 * @author yusu
 */
public sealed interface ActionInvocation
        permits ActionInvocation.Java, ActionInvocation.SpringBean, ActionInvocation.Script {
    /**
     * Invokes a Java class method.
     */
    record Java(String className, String method) implements ActionInvocation {
        public Java {
            className = validClassName(className, "className");
            method = validMethod(method);
        }
    }

    /**
     * Invokes a component resolved by its stable bean name.
     */
    record SpringBean(String beanName, String declaredClass, String method) implements ActionInvocation {
        public SpringBean {
            beanName = requireIdentity(beanName, "beanName");
            declaredClass = validClassName(declaredClass, "declaredClass");
            method = validMethod(method);
        }
    }

    /**
     * Invokes source text through a named script executor.
     */
    record Script(String language, String source) implements ActionInvocation {
        public Script {
            language = ScriptExecutor.requireCanonicalName(language);
            source = requireExpression(source, "source");
        }
    }

    private static String validClassName(String value, String name) {
        String exact = requireIdentity(value, name);
        if (!JavaNames.isClassName(exact)) {
            throw new IllegalArgumentException(name + " must be a valid Java class name: " + value);
        }
        return exact;
    }

    private static String validMethod(String value) {
        String exact = requireIdentity(value, "method");
        if (!JavaNames.isIdentifier(exact)) {
            throw new IllegalArgumentException("method must be a valid Java identifier: " + value);
        }
        return exact;
    }
}
