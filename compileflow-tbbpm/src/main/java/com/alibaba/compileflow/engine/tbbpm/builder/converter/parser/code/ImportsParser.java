package com.alibaba.compileflow.engine.tbbpm.builder.converter.parser.code;

import com.alibaba.compileflow.engine.core.builder.converter.parser.model.ParseContext;
import com.alibaba.compileflow.engine.core.builder.converter.parser.model.XMLSource;
import com.alibaba.compileflow.engine.core.definition.Element;
import com.alibaba.compileflow.engine.core.definition.action.code.Import;
import com.alibaba.compileflow.engine.core.definition.action.code.Imports;
import com.alibaba.compileflow.engine.tbbpm.builder.converter.parser.AbstractTbbpmElementParser;
import com.alibaba.compileflow.engine.tbbpm.definition.TbbpmModelConstants;

/**
 * @author yusu
 */
public class ImportsParser extends AbstractTbbpmElementParser<Imports> {

    @Override
    protected Imports doParse(XMLSource xmlSource, ParseContext parseContext) throws Exception {
        return new Imports();
    }

    @Override
    protected void attachChildElement(Element childElement, Imports element, ParseContext parseContext) {
        if (childElement instanceof Import) {
            element.addImport(((Import) childElement).getValue());
        }
    }

    @Override
    public String getName() {
        return TbbpmModelConstants.IMPORTS;
    }

}
