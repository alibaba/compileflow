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
package com.alibaba.compileflow.engine.tbbpm.builder.converter.parser.action;

import com.alibaba.compileflow.engine.core.builder.converter.parser.model.ParseContext;
import com.alibaba.compileflow.engine.core.builder.converter.parser.model.XMLSource;
import com.alibaba.compileflow.engine.core.definition.Element;
import com.alibaba.compileflow.engine.core.definition.action.code.JavaCode;
import com.alibaba.compileflow.engine.core.definition.action.impl.JavaSourceActionHandle;
import com.alibaba.compileflow.engine.tbbpm.builder.converter.parser.AbstractTbbpmElementParser;
import com.alibaba.compileflow.engine.tbbpm.definition.TbbpmModelConstants;
import org.apache.commons.lang3.StringUtils;

/**
 * Parser for Java class action handle.
 * This parser handles the parsing of Java class source code action configurations.
 *
 * @author yusu
 */
public class JavaSourceActionHandleParser extends AbstractTbbpmElementParser<JavaSourceActionHandle> {

    @Override
    protected JavaSourceActionHandle doParse(XMLSource xmlSource, ParseContext parseContext) throws Exception {
        JavaSourceActionHandle javaSourceActionHandle = new JavaSourceActionHandle();
        String method = xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_METHOD);
        if (StringUtils.isNotBlank(method)) {
            javaSourceActionHandle.setMethod(method);
        }
        javaSourceActionHandle.setCode(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_CODE_LITERAL));
        return javaSourceActionHandle;
    }

    @Override
    protected void attachChildElement(Element childElement, JavaSourceActionHandle element, ParseContext parseContext) {
        if (childElement instanceof JavaCode) {
            element.setCode(((JavaCode) childElement).getCode());
        }
    }

    @Override
    public String getName() {
        return TbbpmModelConstants.JAVA_SOURCE_ACTION_HANDLE;
    }

}
