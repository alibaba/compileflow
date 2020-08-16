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
package com.alibaba.compileflow.engine.process.preruntime.generator.code;

import com.alibaba.compileflow.engine.common.ClassWrapper;
import com.alibaba.compileflow.engine.process.preruntime.generator.constansts.CodeConstants;
import com.alibaba.compileflow.engine.process.preruntime.generator.constansts.Modifier;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yusu
 */
public class FieldTarget extends AbstractCodeTargetSupport implements CodeTarget {

    private String name;
    private ClassWrapper type;
    private List<Modifier> modifiers = new ArrayList<>();
    private String initialization;

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ClassWrapper getType() {
        return type;
    }

    public void setType(ClassWrapper type) {
        this.type = type;
    }

    public String getInitialization() {
        return initialization;
    }

    public void setInitialization(String initialization) {
        this.initialization = initialization;
    }

    public List<Modifier> getModifiers() {
        return modifiers;
    }

    public void setModifiers(List<Modifier> modifiers) {
        this.modifiers = modifiers;
    }

    @Override
    public String generateCode() {
        addFormattedAnnotation(CodeConstants.INDENT);

        addIndent();

        if (isPublic(modifiers)) {
            codeBuffer.append("public ");
        }

        if (isProtected(modifiers)) {
            codeBuffer.append("protected ");
        }

        if (isPrivate(modifiers)) {
            codeBuffer.append("private ");
        }

        if (isStatic(modifiers)) {
            codeBuffer.append("static ");
        }

        if (isFinal(modifiers)) {
            codeBuffer.append("final ");
        }

        codeBuffer.append(type.getName());

        addSpace();
        codeBuffer.append(name);

        if (StringUtils.isNotEmpty(initialization)) {
            codeBuffer.append(" = ");
            codeBuffer.append(initialization);
        }

        addSemicolon();
        return codeBuffer.toString();
    }

}
