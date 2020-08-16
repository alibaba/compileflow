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
package com.alibaba.compileflow.engine.process.preruntime.converter.impl.writer.support.tbbpm;

import com.alibaba.compileflow.engine.definition.common.action.impl.ActionHandle;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.writer.support.AbstractFlowElementWriter;

import javax.xml.stream.XMLStreamWriter;

/**
 * @author yusu
 */
public abstract class AbstractActionHandleWriter<S extends ActionHandle> extends AbstractFlowElementWriter<S> {

    @Override
    protected void doWrite(S element, XMLStreamWriter xsw) throws Exception {
        xsw.writeStartElement("actionHandle");
        writeActionHandleAttr(element, xsw);
        writeVar(element.getVars(), xsw);
        xsw.writeEndElement();
    }

    protected abstract void writeActionHandleAttr(S element, XMLStreamWriter xsw) throws Exception;

}
