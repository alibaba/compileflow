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
import com.alibaba.compileflow.engine.core.definition.action.impl.SpringBeanActionHandle;
import com.alibaba.compileflow.engine.tbbpm.builder.converter.parser.AbstractTbbpmElementParser;
import com.alibaba.compileflow.engine.tbbpm.definition.TbbpmModelConstants;

/**
 * @author wuxiang
 * @author yusu
 */
public class SpringBeanActionHandleParser extends AbstractTbbpmElementParser<SpringBeanActionHandle> {

    @Override
    protected SpringBeanActionHandle doParse(XMLSource xmlSource, ParseContext parseContext) throws Exception {
        SpringBeanActionHandle springBeanActionHandle = new SpringBeanActionHandle();
        springBeanActionHandle.setBean(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_BEAN));
        springBeanActionHandle.setClazz(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_CLAZZ));
        springBeanActionHandle.setMethod(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_METHOD));
        return springBeanActionHandle;
    }

    @Override
    protected void attachChildElement(Element childElement, SpringBeanActionHandle element,
                                      ParseContext parseContext) {

    }

    @Override
    public String getName() {
        return TbbpmModelConstants.SPRING_BEAN_ACTION_HANDLE;
    }

}
