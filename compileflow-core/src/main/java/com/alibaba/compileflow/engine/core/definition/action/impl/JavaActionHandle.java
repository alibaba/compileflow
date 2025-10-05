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
package com.alibaba.compileflow.engine.core.definition.action.impl;

/**
 * Represents an action that invokes a method on a specified Java class.
 * <p>
 * This handle holds the fully qualified class name and the name of the method
 * to be called. The engine will instantiate the class and invoke the method
 * at runtime.
 *
 * @author wuxiang
 * @author yusu
 */
public class JavaActionHandle extends ActionHandle {

    private String clazz;

    private String method = "execute";

    /**
     * Gets the fully qualified name of the Java class to be invoked.
     *
     * @return The class name.
     */
    public String getClazz() {
        return clazz;
    }

    /**
     * Sets the fully qualified name of the Java class.
     *
     * @param clazz The class name.
     */
    public void setClazz(String clazz) {
        this.clazz = clazz;
    }

    /**
     * Gets the name of the method to be invoked on the class.
     * Defaults to "execute" if not specified.
     *
     * @return The method name.
     */
    public String getMethod() {
        return method;
    }

    /**
     * Sets the name of the method to be invoked.
     *
     * @param method The method name.
     */
    public void setMethod(String method) {
        this.method = method;
    }

}
