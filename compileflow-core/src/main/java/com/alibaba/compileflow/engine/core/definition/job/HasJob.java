package com.alibaba.compileflow.engine.core.definition.job;

/**
 * @author yusu
 */
public interface HasJob {

    JobPolicyElement getJobPolicy();

    void setJobPolicy(JobPolicyElement jobPolicyElement);

}
