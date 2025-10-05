package com.alibaba.compileflow.engine.core.runtime.executor.failure;

import com.alibaba.compileflow.engine.core.infrastructure.bean.BeanProvider;
import org.apache.commons.lang3.StringUtils;

/**
 * Resolves a handler identifier (string) into a concrete FailureHandler instance.
 * It first checks for built-in handlers (FAIL_FAST, etc.) and then falls back
 * to looking up a bean from the BeanProvider.
 *
 * @author yusu
 */
public class FailureHandlerResolver {

    public static FailureHandler resolve(String handlerIdentifier) {
        if (StringUtils.isBlank(handlerIdentifier)) {
            // Default to FAIL_FAST if not specified.
            return FailureHandlers.INCIDENT;
        }

        if ("incident".equalsIgnoreCase(handlerIdentifier)) {
            return FailureHandlers.INCIDENT;
        }
        if ("continue".equalsIgnoreCase(handlerIdentifier)) {
            return FailureHandlers.CONTINUE;
        }

        // If it's not a built-in keyword, assume it's a bean name.
        try {
            Object handlerBean = BeanProvider.getBean(handlerIdentifier);
            if (handlerBean instanceof FailureHandler) {
                return (FailureHandler) handlerBean;
            } else {
                throw new IllegalArgumentException("Bean with name '" + handlerIdentifier
                        + "' is not an instance of FailureHandler.");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to resolve FailureHandler bean with name: "
                    + handlerIdentifier, e);
        }
    }

    public static String getFailureHandler(String failureHandler) {
        if (StringUtils.isBlank(failureHandler) || "incident".equals(failureHandler)) {
            return "FailureHandlers.INCIDENT";
        } else if ("continue".equals(failureHandler)) {
            return "FailureHandlers.CONTINUE";
        } else if (BeanProvider.containsBean(failureHandler)) {
            return "FailureHandlerResolver.resolve(\"" + failureHandler + "\")";
        } else {
            throw new IllegalArgumentException("Unknown failure handler: " + failureHandler);
        }
    }

}
