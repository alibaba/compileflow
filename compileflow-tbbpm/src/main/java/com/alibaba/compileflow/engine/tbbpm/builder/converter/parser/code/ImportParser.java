package com.alibaba.compileflow.engine.tbbpm.builder.converter.parser.code;

import com.alibaba.compileflow.engine.core.builder.converter.parser.model.ParseContext;
import com.alibaba.compileflow.engine.core.builder.converter.parser.model.XMLSource;
import com.alibaba.compileflow.engine.core.definition.Element;
import com.alibaba.compileflow.engine.core.definition.action.code.Import;
import com.alibaba.compileflow.engine.tbbpm.builder.converter.parser.AbstractTbbpmElementParser;
import com.alibaba.compileflow.engine.tbbpm.definition.TbbpmModelConstants;

/**
 * @author yusu
 */
public class ImportParser extends AbstractTbbpmElementParser<Import> {

    @Override
    protected Import doParse(XMLSource xmlSource, ParseContext parseContext) throws Exception {
        Import ip = new Import();
        ip.setValue(xmlSource.getElementText());
        return ip;
    }

    @Override
    protected void attachChildElement(Element childElement, Import element, ParseContext parseContext) {
    }

    @Override
    public String getName() {
        return TbbpmModelConstants.IMPORT;
    }

}
