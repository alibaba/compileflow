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
import com.alibaba.compileflow.engine.process.preruntime.generator.constansts.SymbolConstants;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author yusu
 */
public class MethodTarget extends AbstractCodeTargetSupport implements CodeTarget {

    private String name;
    private ClassWrapper returnType;
    private List<ParamTarget> parameterTypes = new ArrayList<>(3);
    private List<ClassWrapper> exceptionTypes = new ArrayList<>(1);
    private List<Modifier> modifiers = new ArrayList<>(2);
    private List<String> bodyLines = new ArrayList<>();

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setReturnType(ClassWrapper returnType) {
        this.returnType = returnType;
    }

    public List<ParamTarget> getParameterTypes() {
        return parameterTypes;
    }

    public void addModifier(Modifier modifier) {
        modifiers.add(modifier);
    }

    public void addParameter(ParamTarget paramTarget) {
        parameterTypes.add(paramTarget);
    }

    public void addException(ClassWrapper exceptionType) {
        exceptionTypes.add(exceptionType);
    }

    @Override
    public void addBodyLine(String line) {
        bodyLines.add(line);
    }

    @Override
    public void addBodyLines(List<String> lines) {
        bodyLines.addAll(lines);
    }

    @Override
    public void appendLine(String line) {
        bodyLines.set(bodyLines.size() - 1, getLastBodyLine() + line);
    }

    @Override
    public void addNewLine() {
        bodyLines.add(SymbolConstants.LINE_BREAK);
    }

    @Override
    public String generateCode() {
        addFormattedAnnotation(CodeConstants.INDENT);

        super.addIndent();

        if (isPublic(modifiers)) {
            codeBuffer.append("public ");
        }

        if (isProtected(modifiers)) {
            codeBuffer.append("protected ");
        }

        if (isPrivate(modifiers)) {
            codeBuffer.append("private ");
        }

        if (isAbstract(modifiers)) {
            codeBuffer.append("abstract ");
        }

        if (isStatic(modifiers)) {
            codeBuffer.append("static ");
        }

        if (isFinal(modifiers)) {
            codeBuffer.append("final ");
        }

        if (returnType == null) {
            codeBuffer.append("void");
        } else {
            codeBuffer.append(returnType.getShortName());
        }

        super.addSpace();
        codeBuffer.append(name);

        super.addOpenParen();
        codeBuffer.append(parameterTypes.stream().map(ParamTarget::generateCode)
            .collect(Collectors.joining(", ")));
        super.addCloseParen();

        if (CollectionUtils.isNotEmpty(exceptionTypes)) {
            codeBuffer.append(" throws ");
            codeBuffer.append(exceptionTypes.stream().map(ClassWrapper::getShortName)
                .collect(Collectors.joining(", ")));
        }

        super.addSpace();
        super.addOpenBrace();

        int indent = CodeConstants.INDENT << 1;

        for (String bodyLine : bodyLines) {
            if (bodyLine.startsWith("}")) {
                indent -= CodeConstants.INDENT;
            }
            super.addNewLine();
            super.addIndent(indent);
            if (!SymbolConstants.LINE_BREAK.equals(bodyLine)) {
                super.addBodyLine(bodyLine);
            }

            if (bodyLine.endsWith("{") || bodyLine.endsWith(":")) {
                indent += CodeConstants.INDENT;
            }
        }

        indent -= CodeConstants.INDENT;
        super.addNewLine();
        super.addIndent(indent);
        super.addCloseBrace();

        return codeBuffer.toString();
    }

    private String getLastBodyLine() {
        return bodyLines.get(bodyLines.size() - 1);
    }

    public boolean isSameMethod(MethodTarget method) {
        if (!name.equals(method.getName())) {
            return false;
        }
        if (method.getParameterTypes() == null) {
            return parameterTypes == null;
        }
        if (parameterTypes == null) {
            return method.getParameterTypes() == null;
        }
        if (method.getParameterTypes().size() != parameterTypes.size()) {
            return false;
        }
        for (int i = 0; i < method.getParameterTypes().size(); i++) {
            if (!method.getParameterTypes().get(i).getType().getName()
                .equals(parameterTypes.get(i).getType().getName())) {
                return false;
            }
        }
        return true;
    }

}
