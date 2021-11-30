/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.process.preruntime.generator.bean;

import com.alibaba.compileflow.engine.common.util.ParamChecker;
import org.springframework.context.ApplicationContext;

/**
 * @author yusu
 */
public class SpringBeanHolder implements BeanHolder {

    private static ApplicationContext context;

    public static SpringBeanHolder of(ApplicationContext applicationContext) {
        SpringBeanHolder springBeanHolder = new SpringBeanHolder();
        SpringBeanHolder.context = applicationContext;
        return springBeanHolder;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getBean(String name) {
        ParamChecker.notEmpty(name, "Bean name is empty");
        ParamChecker.notNull(context, "Spring application context is not injected");

        return (T) context.getBean(name);
    }

    @Override
    public <T> T getBean(Class<T> requiredType) {
        return context.getBean(requiredType);
    }

    @Override
    public boolean containsBean(String name) {
        return context.containsBean(name);
    }

}
