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
import com.alibaba.compileflow.engine.process.preruntime.generator.constansts.Modifier;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author yusu
 */
public class ClassTarget extends AbstractCodeTargetSupport implements CodeTarget {

    private String packageName;

    private Set<ClassWrapper> importedTypes = new HashSet<>();

    private Set<ClassWrapper> staticImports = new HashSet<>();

    private List<Modifier> modifiers = new ArrayList<>();

    private String fullName;

    private String name;

    private ClassWrapper superClass;

    private List<ClassWrapper> superInterfaces = new ArrayList<>(2);

    private List<String> commentLines = new ArrayList<>();

    private List<FieldTarget> fields = new ArrayList<>();

    private List<MethodTarget> methods = new ArrayList<>();

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public void setSuperClass(ClassWrapper superClass) {
        this.superClass = superClass;
    }

    public void addSuperInterface(ClassWrapper superInterface) {
        superInterfaces.add(superInterface);
    }

    public void addCommentLine(String line) {
        commentLines.add(line);
    }

    public void addCommentLines(List<String> lines) {
        commentLines.addAll(lines);
    }

    public List<MethodTarget> getMethods() {
        return methods;
    }

    public void addImportedType(ClassWrapper importedType) {
        if (notContainImportedType(importedType)) {
            importedTypes.add(importedType);
        }
    }

    public void addField(ClassWrapper type, String name, String initialization) {
        if (notContainField(name)) {
            FieldTarget field = new FieldTarget();
            field.setType(type);
            field.setName(name);
            field.setInitialization(initialization);
            fields.add(field);
        }
    }

    public void addModifier(Modifier modifier) {
        modifiers.add(modifier);
    }

    public void addMethod(MethodTarget methodTarget) {
        if (notContainMethod(methodTarget)) {
            methods.add(methodTarget);
        }
    }

    @Override
    public String generateCode() {
        codeBuffer.append("package ");
        codeBuffer.append(packageName);
        addSemicolon();
        addNewLine();
        addNewLine();

        if (CollectionUtils.isNotEmpty(staticImports)) {
            for (ClassWrapper staticImport : staticImports) {
                codeBuffer.append("import static ");
                codeBuffer.append(getImportName(staticImport));
                addSemicolon();
                addNewLine();
            }
            addNewLine();
        }

        if (CollectionUtils.isNotEmpty(importedTypes)) {
            for (ClassWrapper importType : importedTypes) {
                codeBuffer.append("import " + getImportName(importType));
                addSemicolon();
                addNewLine();
            }
            addNewLine();
        }

        for (String commentLine : commentLines) {
            codeBuffer.append(commentLine);
            addNewLine();
        }

        addFormattedAnnotation(0);

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

        codeBuffer.append("class ");
        codeBuffer.append(name);
        addSpace();

        if (superClass != null) {
            codeBuffer.append("extends ");
            codeBuffer.append(superClass.getShortName());
            addSpace();
        }

        if (CollectionUtils.isNotEmpty(superInterfaces)) {
            codeBuffer.append("implements ");
            codeBuffer.append(superInterfaces.stream().map(ClassWrapper::getShortRawName)
                .collect(Collectors.joining(", ")));
            addSpace();
        }

        addOpenBrace();
        addNewLine();

        for (FieldTarget field : fields) {
            addNewLine();
            codeBuffer.append(field.generateCode());
        }

        for (MethodTarget method : methods) {
            addNewLine();
            addNewLine();
            codeBuffer.append(method.generateCode());
        }

        addNewLine();
        addNewLine();
        addCloseBrace();

        return codeBuffer.toString();
    }

    private String getImportName(ClassWrapper importType) {
        return importType.getPackageName() + "." + importType.getShortRawName();
    }

    private boolean notContainImportedType(ClassWrapper classWrapper) {
        return importedTypes.stream().map(this::getImportName).noneMatch(
            importName -> importName.equals(getImportName(classWrapper)));
    }

    private boolean notContainField(String name) {
        return fields.stream().noneMatch(field -> field.getName().equals(name));
    }

    private boolean notContainMethod(MethodTarget methodTarget) {
        return methods.stream().noneMatch(method -> method.isSameMethod(methodTarget));
    }

}
