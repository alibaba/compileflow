package com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.support.tbbpm;

import com.alibaba.compileflow.engine.definition.common.Element;
import com.alibaba.compileflow.engine.definition.common.action.IAction;
import com.alibaba.compileflow.engine.definition.common.action.IInAction;
import com.alibaba.compileflow.engine.definition.common.action.IOutAction;
import com.alibaba.compileflow.engine.definition.tbbpm.TbbpmModelConstants;
import com.alibaba.compileflow.engine.definition.tbbpm.WaitEventTaskNode;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.model.ParseContext;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.model.XMLSource;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.support.AbstractTbbpmElementParser;

/**
 * @author wuxiang
 * since 2021/6/23
 **/
public class WaitEventTaskParser extends AbstractTbbpmElementParser<WaitEventTaskNode> {

    @Override
    public String getName() {
        return TbbpmModelConstants.WAIT_EVENT_TASK;
    }

    @Override
    protected WaitEventTaskNode doParse(XMLSource xmlSource, ParseContext parseContext) throws Exception {
        WaitEventTaskNode waitEventNode = new WaitEventTaskNode();
        waitEventNode.setId(xmlSource.getString("id"));
        waitEventNode.setName(xmlSource.getString("name"));
        waitEventNode.setTag(xmlSource.getString("tag"));
        waitEventNode.setEventName(xmlSource.getString("eventName"));
        waitEventNode.setDescription(xmlSource.getString("description"));
        waitEventNode.setG(xmlSource.getString("g"));
        return waitEventNode;
    }

    @Override
    protected void attachChildElement(Element childElement, WaitEventTaskNode element, ParseContext parseContext) {
        if (childElement instanceof IInAction) {
            element.setInAction((IAction)childElement);
        } else if (childElement instanceof IOutAction) {
            element.setOutAction((IAction)childElement);
        }
    }
}
