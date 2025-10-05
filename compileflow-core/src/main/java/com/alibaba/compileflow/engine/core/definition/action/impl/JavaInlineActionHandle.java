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

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yusu
 */
public class JavaInlineActionHandle extends JavaCodeActionHandle {

    private List<String> imports = new ArrayList<>();
    private Mode mode = Mode.BLOCK;

    public List<String> getImports() {
        return imports;
    }

    public void setImports(List<String> imports) {
        this.imports = imports;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public enum Mode {
        BLOCK, EXPR;

        public static Mode of(String mode) {
            if (StringUtils.isBlank(mode)) {
                return BLOCK;
            }
            switch (mode) {
                case "expr":
                    return EXPR;
                case "block":
                    return BLOCK;
                default:
                    throw new IllegalArgumentException("Unsupported code@mode: " + mode);
            }
        }
    }

}
