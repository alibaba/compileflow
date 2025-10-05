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
import com.alibaba.compileflow.engine.core.definition.action.code.Imports;
import com.alibaba.compileflow.engine.core.definition.action.code.JavaCode;
import com.alibaba.compileflow.engine.core.definition.action.impl.JavaInlineActionHandle;
import com.alibaba.compileflow.engine.tbbpm.builder.converter.parser.AbstractTbbpmElementParser;
import com.alibaba.compileflow.engine.tbbpm.definition.TbbpmModelConstants;

/**
 * Parser for java-inline.
 *
 * @author yusu
 */
public class JavaInlineActionHandleParser extends AbstractTbbpmElementParser<JavaInlineActionHandle> {

    @Override
    protected JavaInlineActionHandle doParse(XMLSource xmlSource, ParseContext parseContext) throws Exception {
        JavaInlineActionHandle javaInlineActionHandle = new JavaInlineActionHandle();
        javaInlineActionHandle.setCode(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_CODE_LITERAL));
        return javaInlineActionHandle;
    }

    @Override
    protected void attachChildElement(Element childElement, JavaInlineActionHandle element, ParseContext parseContext) {
        if (childElement instanceof Imports) {
            element.setImports(((Imports) childElement).getImports());
        } else if (childElement instanceof JavaCode) {
            element.setCode(((JavaCode) childElement).getCode());
            element.setMode(JavaInlineActionHandle.Mode.of(((JavaCode) childElement).getMode()));
        }
    }

    @Override
    public String getName() {
        return TbbpmModelConstants.JAVA_INLINE_ACTION_HANDLE;
    }
}
