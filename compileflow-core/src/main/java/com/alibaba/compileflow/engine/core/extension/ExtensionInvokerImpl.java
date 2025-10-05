package com.alibaba.compileflow.engine.core.extension;

import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.ErrorCode;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

/**
 * Enhanced implementation of {@link ExtensionInvoker} supporting strategies and fault tolerance.
 *
 * @author yusu
 */
@Service
public class ExtensionInvokerImpl extends ExtensionInvoker implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExtensionInvokerImpl.class);


    @Override
    public <T, E extends Extension> T invokeFirst(String extensionCode, ExtensionCallback<E, T> extensionCallback) {
        return invokeFirst(null, extensionCode, extensionCallback);
    }

    @Override
    public <T, E extends Extension> T invokeFirst(ExtensionContext context, String extensionCode,
                                                  ExtensionCallback<E, T> extensionCallback) {
        Extension extension = getFirstMatchingExtension(context, extensionCode);

        try {
            T result = extensionCallback.execute((E) extension);
            return result;
        } catch (Exception e) {
            throw new CompileFlowException.SystemException(
                    ErrorCode.CF_CONFIG_004,
                    "Failed to invoke extension: extensionCode is " + extensionCode,
                    e
            );
        }
    }

    @Override
    public <E extends Extension> void invokeFirst(String extensionCode, ExtensionAction<E> extensionAction) {
        invokeFirst(null, extensionCode, extensionAction);
    }

    @Override
    public <E extends Extension> void invokeFirst(ExtensionContext context, String extensionCode, ExtensionAction<E> extensionAction) {
        Extension extension = getFirstMatchingExtension(context, extensionCode);

        try {
            extensionAction.execute((E) extension);
        } catch (Exception e) {
            throw new CompileFlowException.SystemException(
                    ErrorCode.CF_CONFIG_004,
                    "Failed to invoke extension: extensionCode is " + extensionCode,
                    e
            );
        }
    }

    @Override
    public <T, E extends Extension> T invokeFirstSafe(String extensionCode,
                                                      ExtensionCallback<E, T> extensionCallback,
                                                      T fallbackValue) {
        return invokeFirstSafe(null, extensionCode, extensionCallback, fallbackValue);
    }

    @Override
    public <T, E extends Extension> T invokeFirstSafe(ExtensionContext context, String extensionCode,
                                                      ExtensionCallback<E, T> extensionCallback,
                                                      T fallbackValue) {
        try {
            return invokeFirst(context, extensionCode, extensionCallback);
        } catch (Exception e) {
            LOGGER.debug("Extension {} failed, using fallback value", extensionCode, e);
            return fallbackValue;
        }
    }

    @Override
    public <T, E extends Extension> List<T> invokeAll(String extensionCode,
                                                      ExtensionCallback<E, T> extensionCallback) {
        return invokeAll(null, extensionCode, extensionCallback);
    }

    @Override
    public <T, E extends Extension> List<T> invokeAll(ExtensionContext context, String extensionCode,
                                                      ExtensionCallback<E, T> extensionCallback) {
        List<Extension> matchingExtensions = getAllMatchingExtensions(context, extensionCode);
        if (CollectionUtils.isEmpty(matchingExtensions)) {
            return Collections.emptyList();
        }

        List<T> results = new ArrayList<>();

        for (Extension extension : matchingExtensions) {
            try {
                T result = extensionCallback.execute((E) extension);
                if (result != null) {
                    results.add(result);
                }
            } catch (Exception e) {
                LOGGER.warn("Extension {} failed in invokeAll", extensionCode, e);
            }
        }

        return results;
    }

    @Override
    public <T, E extends Extension> T invokeChain(String extensionCode,
                                                  T initialValue,
                                                  ExtensionChainCallback<E, T> chainCallback) {
        return invokeChain(null, extensionCode, initialValue, chainCallback);
    }

    @Override
    public <T, E extends Extension> T invokeChain(ExtensionContext context, String extensionCode,
                                                  T initialValue,
                                                  ExtensionChainCallback<E, T> chainCallback) {
        List<Extension> matchingExtensions = getAllMatchingExtensions(context, extensionCode);
        if (CollectionUtils.isEmpty(matchingExtensions)) {
            return initialValue;
        }

        T currentValue = initialValue;

        for (Extension extension : matchingExtensions) {
            try {
                currentValue = chainCallback.execute((E) extension, currentValue);
            } catch (Exception e) {
                LOGGER.error("Extension chain {} failed", extensionCode, e);
                break;
            }
        }

        return currentValue;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, R, E extends Extension> R invokeReduce(String extensionCode,
                                                      ExtensionCallback<E, T> extensionCallback,
                                                      R identity,
                                                      BinaryOperator<R> reducer) {
        return invokeReduce(null, extensionCode, extensionCallback, identity, reducer);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, R, E extends Extension> R invokeReduce(ExtensionContext context, String extensionCode,
                                                      ExtensionCallback<E, T> extensionCallback,
                                                      R identity,
                                                      BinaryOperator<R> reducer) {
        List<Extension> matchingExtensions = getAllMatchingExtensions(context, extensionCode);
        if (CollectionUtils.isEmpty(matchingExtensions)) {
            return identity;
        }

        List<T> results = matchingExtensions.stream()
                .map(extension -> {
                    try {
                        return extensionCallback.execute((E) extension);
                    } catch (Exception e) {
                        LOGGER.warn("Extension {} failed in invokeReduce", extensionCode, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (!results.isEmpty() && identity.getClass().isAssignableFrom(results.get(0).getClass())) {
            return results.stream()
                    .map(result -> (R) result)
                    .reduce(identity, reducer);
        }

        return identity;
    }

    @Override
    public Extension getFirstMatchingExtension(ExtensionContext context, String extensionCode) {
        List<ExtensionRealizationSpec> extensions = getExtensionSpecs(extensionCode);
        if (CollectionUtils.isEmpty(extensions)) {
            throw new CompileFlowException.ConfigurationException(
                    ErrorCode.CF_CONFIG_004,
                    "Failed to find extension: extensionCode is " + extensionCode
            );
        }
        return extensions.stream()
                .map(ExtensionRealizationSpec::getTarget)
                .filter(e -> context == null || e.support(context))
                .findFirst()
                .orElseThrow(() -> new CompileFlowException.ConfigurationException(
                        ErrorCode.CF_CONFIG_004,
                        "Failed to find extension: extensionCode is " + extensionCode
                ));
    }

    @Override
    public List<Extension> getAllMatchingExtensions(ExtensionContext context, String extensionCode) {
        List<ExtensionRealizationSpec> specs = getExtensionSpecs(extensionCode);
        if (CollectionUtils.isEmpty(specs)) {
            return Collections.emptyList();
        }
        return specs.stream()
                .map(ExtensionRealizationSpec::getTarget)
                .filter(e -> context == null || e.support(context))
                .collect(Collectors.toList());
    }

    private List<ExtensionRealizationSpec> getExtensionSpecs(String extensionCode) {
        List<ExtensionRealizationSpec> extensions = ExtensionManager.getInstance().getExtensions(extensionCode);
        return CollectionUtils.isEmpty(extensions) ? Collections.emptyList() : extensions;
    }


    @Override
    public void afterPropertiesSet() {
        instance = this;
        LOGGER.debug("Enhanced ExtensionInvoker initialized");
    }

}
