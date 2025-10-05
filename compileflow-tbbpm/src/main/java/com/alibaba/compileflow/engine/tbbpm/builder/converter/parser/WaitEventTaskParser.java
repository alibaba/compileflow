package com.alibaba.compileflow.engine.tbbpm.builder.converter.parser;

import com.alibaba.compileflow.engine.core.builder.converter.parser.model.ParseContext;
import com.alibaba.compileflow.engine.core.builder.converter.parser.model.XMLSource;
import com.alibaba.compileflow.engine.core.definition.Element;
import com.alibaba.compileflow.engine.core.definition.action.IAction;
import com.alibaba.compileflow.engine.core.definition.action.IInAction;
import com.alibaba.compileflow.engine.core.definition.action.IOutAction;
import com.alibaba.compileflow.engine.tbbpm.definition.TbbpmModelConstants;
import com.alibaba.compileflow.engine.tbbpm.definition.WaitEventTaskNode;

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
        waitEventNode.setId(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_ID));
        waitEventNode.setName(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_NAME));
        waitEventNode.setTag(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_TAG));
        waitEventNode.setEvent(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_EVENT));
        waitEventNode.setDescription(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_DESCRIPTION));
        waitEventNode.setG(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_G));
        return waitEventNode;
    }

    @Override
    protected void attachChildElement(Element childElement, WaitEventTaskNode element, ParseContext parseContext) {
        if (childElement instanceof IInAction) {
            element.setInAction((IAction) childElement);
        } else if (childElement instanceof IOutAction) {
            element.setOutAction((IAction) childElement);
        }
    }
}
