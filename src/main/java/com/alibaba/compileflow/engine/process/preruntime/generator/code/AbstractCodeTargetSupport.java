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

import com.alibaba.compileflow.engine.process.preruntime.generator.constansts.CodeConstants;
import com.alibaba.compileflow.engine.process.preruntime.generator.constansts.Modifier;
import com.alibaba.compileflow.engine.process.preruntime.generator.constansts.SymbolConstants;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yusu
 */
public abstract class AbstractCodeTargetSupport implements CodeTargetSupport {

    protected StringBuffer codeBuffer = new StringBuffer();

    private CodeTargetSupport classTarget;

    private List<String> annotations = new ArrayList<>(2);

    @Override
    public CodeTargetSupport getClassTarget() {
        return classTarget;
    }

    public void setClassTarget(CodeTargetSupport classTarget) {
        this.classTarget = classTarget;
    }

    public List<String> getAnnotations() {
        return annotations;
    }

    public void addAnnotation(String annotation) {
        annotations.add(annotation);
    }

    @Override
    public void addBodyLine(String line) {
        codeBuffer.append(line);
    }

    @Override
    public void addBodyLines(List<String> lines) {
        codeBuffer.append(lines);
    }

    @Override
    public void appendLine(String line) {
        codeBuffer.append(line);
    }

    @Override
    public void addNewLine() {
        codeBuffer.append(SymbolConstants.LINE_BREAK);
    }

    @Override
    public void addSemicolon() {
        codeBuffer.append(SymbolConstants.SEMICOLON);
    }

    @Override
    public void addSpace() {
        codeBuffer.append(SymbolConstants.SPACE);
    }

    @Override
    public void addOpenBrace() {
        codeBuffer.append(SymbolConstants.OPEN_BRACE);
    }

    @Override
    public void addCloseBrace() {
        codeBuffer.append(SymbolConstants.CLOSE_BRACE);
    }

    @Override
    public void addOpenParen() {
        codeBuffer.append(SymbolConstants.OPEN_PAREN);
    }

    @Override
    public void addCloseParen() {
        codeBuffer.append(SymbolConstants.CLOSE_PAREN);
    }

    @Override
    public void addIndent() {
        addIndent(CodeConstants.INDENT);
    }

    @Override
    public void addIndent(int indent) {
        for (int i = 0; i < indent; i++) {
            addSpace();
        }
    }

    public boolean addFormattedAnnotation(int indent) {
        if (CollectionUtils.isNotEmpty(annotations)) {
            for (String annotation : annotations) {
                addIndent(indent);
                codeBuffer.append(annotation);
                codeBuffer.append("\n");
            }
            return true;
        }
        return false;
    }

    protected boolean isPublic(List<Modifier> modifiers) {
        return isModifierMatch(modifiers, Modifier.PUBLIC);
    }

    protected boolean isProtected(List<Modifier> modifiers) {
        return isModifierMatch(modifiers, Modifier.PROTECTED);
    }

    protected boolean isPrivate(List<Modifier> modifiers) {
        return CollectionUtils.isEmpty(modifiers) || isModifierMatch(modifiers, Modifier.PRIVATE);
    }

    protected boolean isAbstract(List<Modifier> modifiers) {
        return isModifierMatch(modifiers, Modifier.ABSTRACT);
    }

    protected boolean isStatic(List<Modifier> modifiers) {
        return isModifierMatch(modifiers, Modifier.STATIC);
    }

    protected boolean isFinal(List<Modifier> modifiers) {
        return isModifierMatch(modifiers, Modifier.FINAL);
    }

    protected boolean isModifierMatch(List<Modifier> modifiers, Modifier modifier) {
        return modifiers.stream().anyMatch(modifier::equals);
    }

}
