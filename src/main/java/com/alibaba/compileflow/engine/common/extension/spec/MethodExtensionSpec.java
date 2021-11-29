package com.alibaba.compileflow.engine.common.extension.spec;

import com.alibaba.compileflow.engine.common.extension.ExtensionPoint;
import com.alibaba.compileflow.engine.common.extension.annotation.Extension;
import com.alibaba.compileflow.engine.common.extension.annotation.Extensions;
import com.alibaba.compileflow.engine.common.extension.consts.FlatType;
import com.alibaba.compileflow.engine.common.extension.invoker.FastMethodInvoker;
import com.alibaba.compileflow.engine.common.extension.invoker.MethodInvoker;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

/**
 * @author yusu
 */
public class MethodExtensionSpec extends ExtensionSpec {

    private MethodInvoker methodInvoker;

    private Method method;

    public static MethodExtensionSpec of(Extensions extensionsAnnotation,
                                         Extension extensionAnnotation,
                                         ExtensionPoint extensions,
                                         Method method) {
        MethodExtensionSpec extensionSpec = new MethodExtensionSpec();
        extensionSpec.setExtension(extensions);
        extensionSpec.setScenario(extensionsAnnotation.scenario());
        extensionSpec.setPriority(extensionsAnnotation.priority());
        extensionSpec.setCode(extensionAnnotation.code());
        extensionSpec.setName(extensionAnnotation.name());
        extensionSpec.setDescription(extensionAnnotation.description());
        extensionSpec.setGroup(extensionAnnotation.group());
        extensionSpec.setReducePolicy(extensionAnnotation.reducePolicy());
        extensionSpec.setMethodInvoker(FastMethodInvoker.of(extensions, method));
        extensionSpec.setMethod(method);
        return extensionSpec;
    }

    @Override
    public boolean isCollectionFlat() {
        return FlatType.COLLECTION_FLAT.equals(getFlatType());
    }

    @Override
    public boolean isMapFlat() {
        return FlatType.MAP_FLAT.equals(getFlatType());
    }

    private FlatType getFlatType() {
        if (Collection.class.isAssignableFrom(method.getReturnType())) {
            return FlatType.COLLECTION_FLAT;
        }
        if (Map.class.isAssignableFrom(method.getReturnType())) {
            return FlatType.MAP_FLAT;
        }
        return FlatType.NONE;
    }

    public MethodInvoker getMethodInvoker() {
        return methodInvoker;
    }

    public void setMethodInvoker(MethodInvoker methodInvoker) {
        this.methodInvoker = methodInvoker;
    }

    public Method getMethod() {
        return method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

}
