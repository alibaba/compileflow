package com.alibaba.compileflow.engine.tbbpm.builder.converter.parser.code;

import com.alibaba.compileflow.engine.core.builder.converter.parser.model.ParseContext;
import com.alibaba.compileflow.engine.core.builder.converter.parser.model.XMLSource;
import com.alibaba.compileflow.engine.core.definition.Element;
import com.alibaba.compileflow.engine.core.definition.action.code.JavaCode;
import com.alibaba.compileflow.engine.tbbpm.builder.converter.parser.AbstractTbbpmElementParser;
import com.alibaba.compileflow.engine.tbbpm.definition.TbbpmModelConstants;

/**
 * @author yusu
 */
public class JavaCodeParser extends AbstractTbbpmElementParser<JavaCode> {

    @Override
    protected JavaCode doParse(XMLSource xmlSource, ParseContext parseContext) throws Exception {
        JavaCode javaCode = new JavaCode();
        javaCode.setMode(xmlSource.getString(TbbpmModelConstants.ATTRIBUTE_MODE));
        javaCode.setCode(xmlSource.getElementText());
        return javaCode;
    }

    @Override
    protected void attachChildElement(Element childElement, JavaCode element, ParseContext parseContext) {

    }

    @Override
    public String getName() {
        return TbbpmModelConstants.CODE;
    }

}
