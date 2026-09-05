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
package com.alibaba.compileflow.engine.test.support.extensions;

import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.test.support.helpers.ProcessEngineTestFactory;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

public class ProcessEngineExtension implements AfterEachCallback, ParameterResolver {
    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(ProcessEngineExtension.class);
    private static final String ENGINE_KEY = "processEngine";

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        ProcessEngine engine = context.getStore(NAMESPACE).get(ENGINE_KEY, ProcessEngine.class);

        if (engine != null) {
            engine.close();
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType() == ProcessEngine.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return extensionContext
            .getStore(NAMESPACE)
            .computeIfAbsent(ENGINE_KEY, key -> createEngine(extensionContext), ProcessEngine.class);
    }

    private ProcessEngine createEngine(ExtensionContext context) {
        // Check method-level annotations first
        if (context.getTestMethod().isPresent() && context
                    .getTestMethod()
                    .get()
                    .isAnnotationPresent(BpmnEngine.class)) {
            return ProcessEngineTestFactory.createBpmn();
        }

        if (context.getTestMethod().isPresent() && context
                    .getTestMethod()
                    .get()
                    .isAnnotationPresent(TbbpmEngine.class)) {
            return ProcessEngineTestFactory.createTbbpm();
        }
        // Check class-level annotations
        if (context.getTestClass().isPresent() && context.getTestClass().get().isAnnotationPresent(BpmnEngine.class)) {
            return ProcessEngineTestFactory.createBpmn();
        }

        if (context.getTestClass().isPresent() && context.getTestClass().get().isAnnotationPresent(TbbpmEngine.class)) {
            return ProcessEngineTestFactory.createTbbpm();
        }
        // Default to BPMN engine
        return ProcessEngineTestFactory.createBpmn();
    }
}
