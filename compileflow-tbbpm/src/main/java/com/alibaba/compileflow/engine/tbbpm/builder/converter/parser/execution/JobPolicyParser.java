package com.alibaba.compileflow.engine.tbbpm.builder.converter.parser.execution;

import com.alibaba.compileflow.engine.core.builder.converter.parser.model.ParseContext;
import com.alibaba.compileflow.engine.core.builder.converter.parser.model.XMLSource;
import com.alibaba.compileflow.engine.core.definition.Element;
import com.alibaba.compileflow.engine.core.definition.job.JobPolicy;
import com.alibaba.compileflow.engine.tbbpm.builder.converter.parser.AbstractTbbpmElementParser;
import com.alibaba.compileflow.engine.tbbpm.definition.TbbpmModelConstants;

/**
 * @author yusu
 */
public class JobPolicyParser extends AbstractTbbpmElementParser<JobPolicy> {

    @Override
    protected JobPolicy doParse(XMLSource xmlSource, ParseContext parseContext) throws Exception {
        JobPolicy jobPolicy = new JobPolicy();
        jobPolicy.setTimeout(xmlSource.getStringOrDefault(TbbpmModelConstants.JOB_TIMEOUT, "PT0S"));
        jobPolicy.setRetry(xmlSource.getStringOrDefault(TbbpmModelConstants.JOB_RETRY, "R0/PT0S"));
        jobPolicy.setRetryOn(xmlSource.getStringOrDefault(TbbpmModelConstants.JOB_RETRY_ON, "never"));
        jobPolicy.setOnFailure(xmlSource.getStringOrDefault(TbbpmModelConstants.JOB_ON_FAILURE, "incident"));
        return jobPolicy;
    }

    @Override
    protected void attachChildElement(Element childElement, JobPolicy element, ParseContext parseContext) {

    }

    @Override
    public String getName() {
        return TbbpmModelConstants.JOB_POLICY;
    }

}
