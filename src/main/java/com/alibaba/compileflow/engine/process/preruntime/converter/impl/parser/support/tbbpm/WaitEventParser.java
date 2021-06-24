package com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.support.tbbpm;

import com.alibaba.compileflow.engine.definition.common.Element;
import com.alibaba.compileflow.engine.definition.tbbpm.TbbpmModelConstants;
import com.alibaba.compileflow.engine.definition.tbbpm.WaitEventNode;
import com.alibaba.compileflow.engine.definition.tbbpm.WaitTaskNode;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.model.ParseContext;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.model.XMLSource;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.support.AbstractTbbpmElementParser;

/**
 * @author wuxiang
 * since 2021/6/23
 **/
public class WaitEventParser extends AbstractTbbpmElementParser<WaitEventNode> {

    @Override
    public String getName() {
        return TbbpmModelConstants.WAIT_EVENT_TASK;
    }

    @Override
    protected WaitEventNode doParse(XMLSource xmlSource, ParseContext parseContext) throws Exception {
        WaitEventNode waitEventNode = new WaitEventNode();
        waitEventNode.setId(xmlSource.getString("id"));
        waitEventNode.setName(xmlSource.getString("name"));
        waitEventNode.setTag(xmlSource.getString("tag"));
        waitEventNode.setEventName(xmlSource.getString("eventName"));
        waitEventNode.setDescription(xmlSource.getString("description"));
        waitEventNode.setG(xmlSource.getString("g"));
        return waitEventNode;
    }

    @Override
    protected void attachChildElement(Element childElement, WaitEventNode element, ParseContext parseContext) {

    }
}
