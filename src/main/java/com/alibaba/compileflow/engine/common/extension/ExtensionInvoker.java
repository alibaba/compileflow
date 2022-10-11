package com.alibaba.compileflow.engine.common.extension;

import com.alibaba.compileflow.engine.common.extension.constant.ReducePolicy;
import com.alibaba.compileflow.engine.common.extension.filter.ReduceFilter;
import com.alibaba.compileflow.engine.common.extension.invoker.MethodInvoker;
import com.alibaba.compileflow.engine.common.extension.spec.ExtensionPointSpec;
import com.alibaba.compileflow.engine.common.extension.spec.ExtensionSpec;
import com.alibaba.compileflow.engine.common.extension.spec.MethodExtensionSpec;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author yusu
 */
public class ExtensionInvoker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExtensionInvoker.class);

    public static ExtensionInvoker getInstance() {
        return Holder.INSTANCE;
    }

    public <T> T invoke(String extensionCode, ReduceFilter reduceFilter, Object... args) {
        List<ExtensionSpec> extensions = ExtensionManager.getInstance().getExtensions(extensionCode);
        if (CollectionUtils.isEmpty(extensions)) {
            return null;
        }

        return invoke(extensions, reduceFilter, args);
    }

    public <T> T invoke(String extensionCode, Predicate predicate, Object... args) {
        ExtensionPointSpec extensionPointSpec = ExtensionManager.getInstance().getExtensionPoint(extensionCode);
        if (extensionPointSpec == null) {
            return null;
        }

        ReducePolicy reducePolicy = extensionPointSpec.getReducePolicy();
        ReduceFilter reduceFilter = ReduceFilter.of(predicate, reducePolicy);
        return invoke(extensionCode, reduceFilter, args);
    }

    public <T> T invoke(String extensionCode, Class<? extends IExtensionPoint> extension, Object... args) {
        List<ExtensionSpec> extensions = ExtensionManager.getInstance().getExtensions(extensionCode);
        if (CollectionUtils.isEmpty(extensions)) {
            return null;
        }
        ExtensionSpec extensionSpec = extensions.stream()
            .filter(em -> extension.isInstance(em.getExtension()))
            .findFirst().orElse(null);

        return (T) invoke(extensionSpec, args);
    }

    public <T> T invoke(String extensionCode, Class<? extends IExtensionPoint> extension,
                        ReduceFilter reduceFilter, Object... args) {
        List<ExtensionSpec> extensions = ExtensionManager.getInstance().getExtensions(extensionCode);
        if (CollectionUtils.isEmpty(extensions)) {
            return null;
        }
        List<ExtensionSpec> extensionSpecs = extensions.stream()
            .filter(em -> extension.isInstance(em.getExtension()))
            .collect(Collectors.toList());

        return (T) invoke(extensionSpecs, reduceFilter, args);
    }

    private <T> T invoke(List<ExtensionSpec> extensions, ReduceFilter reduceFilter, Object[] args) {
        ReducePolicy reducePolicy = reduceFilter.getReducePolicy();
        Predicate predicate = reduceFilter.getPredicate();
        ExtensionSpec extensionSpec = extensions.get(0);
        if (ReducePolicy.FISRT_MATCH.equals(reducePolicy)) {
            if (predicate == null) {
                return (T) invoke(extensionSpec, args);
            }

            return (T) extensions.stream().map(em -> invoke(em, args))
                .filter(predicate::test)
                .findFirst().orElse(null);
        }

        if (ReducePolicy.ALL_MATCH.equals(reducePolicy)) {
            List results = extensions.stream().map(em -> invoke(em, args))
                .filter(t -> predicate == null || predicate.test(t))
                .collect(Collectors.toList());

            if (extensionSpec.isCollectionFlat()) {
                List<List> listsResults = (List<List>) results;
                return (T) listsResults.stream().flatMap(List::stream).collect(Collectors.toList());
            }
            if (extensionSpec.isMapFlat()) {
                List<Map> listMapResults = (List<Map>) results;
                Map mapResults = new HashMap();
                for (int i = listMapResults.size() - 1; i >= 0; i--) {
                    mapResults.putAll(listMapResults.get(i));
                }
                return (T) mapResults;
            }
            return (T) results;
        }

        return null;
    }

    private Object invoke(ExtensionSpec extensionSpec, Object... args) {
        return Optional.ofNullable(extensionSpec).map(spec -> (MethodExtensionSpec) spec)
            .map(MethodExtensionSpec::getMethodInvoker).map(
                methodInvoker -> this.invoke(methodInvoker, args)).orElse(null);
    }

    private Object invoke(MethodInvoker methodInvoker, Object[] args) {
        try {
            return methodInvoker.invoke(args);
        } catch (Exception e) {
            LOGGER.error("Invoke method error", e);
            throw new RuntimeException(e);
        }
    }

    private static class Holder {
        private static final ExtensionInvoker INSTANCE = new ExtensionInvoker();
    }

}
