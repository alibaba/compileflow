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
package com.alibaba.compileflow.engine.core.builder.generator.constants;

/**
 * @author yusu
 */
public final class CodeConstants {

    /**
     * Number of spaces used for indentation when spaces are selected.
     * Configurable via system property 'compileflow.codegen.indent-spaces'.
     */
    public static final int INDENT = Integer.getInteger("compileflow.codegen.indent-spaces", 4);

    /**
     * Whether to use tab characters for indentation instead of spaces.
     * Configurable via system property 'compileflow.codegen.indent.use-tabs'.
     */
    public static final boolean USE_TABS = Boolean.getBoolean("compileflow.codegen.indent.use-tabs");

    /**
     * Single indent unit used by generators (either a tab or N spaces).
     */
    public static final String INDENT_UNIT = USE_TABS ? "\t" : repeat(' ', INDENT);

    private CodeConstants() {
        // no instances
    }

    private static String repeat(char ch, int count) {
        if (count <= 0) {
            return "";
        }
        char[] arr = new char[count];
        for (int i = 0; i < count; i++) {
            arr[i] = ch;
        }
        return new String(arr);
    }

}
