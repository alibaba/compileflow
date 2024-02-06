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
package com.alibaba.compileflow.engine;

import java.util.Map;

/**
 * @author wuxiang
 * @author yusu
 */
public interface ProcessEngine<T extends FlowModel> {

    Map<String, Object> execute(String code, Map<String, Object> context);

    Map<String, Object> trigger(String code, String tag, Map<String, Object> context);

    Map<String, Object> trigger(String code, String tag, String event, Map<String, Object> context);

    void preCompile(String... codes);

    void preCompile(ClassLoader classLoader, String... codes);

    T load(String code);

    String getJavaCode(String code);

    String getTestCode(String code);

    /***
     ** --- Compared with APIs above, following APIs add content of bpm as input directly ---
     */
    Map<String, Object> execute(String code, Map<String, Object> context, String content);

    Map<String, Object> trigger(String code, String tag, Map<String, Object> context, String content);

    Map<String, Object> trigger(String code, String tag, String event, Map<String, Object> context, String content);

    void preCompile(Map<String, String> code2ContentMap);

    void preCompile(ClassLoader classLoader, Map<String, String> code2ContentMap);

    T load(String code, String content);

    String getJavaCode(String code, String content);

    String getTestCode(String code, String content);

}
