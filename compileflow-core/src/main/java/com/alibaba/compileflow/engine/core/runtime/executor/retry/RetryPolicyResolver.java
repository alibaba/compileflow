package com.alibaba.compileflow.engine.core.runtime.executor.retry;

import com.alibaba.compileflow.engine.core.infrastructure.bean.BeanProvider;
import org.apache.commons.lang3.StringUtils;

/**
 * @author yusu
 */
public class RetryPolicyResolver {

    public static RetryPolicy resolve(String policyIdentifier) {
        if (StringUtils.isBlank(policyIdentifier) || "never".equalsIgnoreCase(policyIdentifier)) {
            return RetryPolicies.NEVER;
        }
        if ("transient".equalsIgnoreCase(policyIdentifier)) {
            return RetryPolicies.TRANSIENT;
        }
        if ("always".equalsIgnoreCase(policyIdentifier)) {
            return RetryPolicies.ALWAYS;
        }
        // If it's not a built-in keyword, assume it's a bean name.
        try {
            Object retryPolicyBean = BeanProvider.getBean(policyIdentifier);
            if (retryPolicyBean instanceof RetryPolicy) {
                return (RetryPolicy) retryPolicyBean;
            } else {
                throw new IllegalArgumentException("Bean with name '" + policyIdentifier
                        + "' is not an instance of RetryPolicy.");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to resolve RetryPolicy bean with name: "
                    + policyIdentifier, e);
        }
    }

    public static String getRetryPolicy(String retryPolicy) {
        if (StringUtils.isBlank(retryPolicy) || "never".equals(retryPolicy)) {
            return "RetryPolicies.NEVER";
        } else if ("transient".equals(retryPolicy)) {
            return "RetryPolicies.TRANSIENT";
        } else if ("always".equals(retryPolicy)) {
            return "RetryPolicies.ALWAYS";
        } else if (BeanProvider.containsBean(retryPolicy)) {
            return "RetryPolicyResolver.resolve(" + retryPolicy + ")";
        } else {
            throw new IllegalArgumentException("Unknown retry policy: " + retryPolicy);
        }
    }

}
