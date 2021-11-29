package com.alibaba.compileflow.engine.common.extension.spec;

import com.alibaba.compileflow.engine.common.extension.ExtensionPoint;
import com.alibaba.compileflow.engine.common.extension.annotation.Extension;

import java.lang.reflect.Method;

/**
 * @author yusu
 */
public class MethodExtensionPointSpec extends ExtensionPointSpec {

    private Method method;

    public static MethodExtensionPointSpec of(Extension extensionAnnotation,
                                              Class<? extends ExtensionPoint> extensionPointClass,
                                              Method method) {
        MethodExtensionPointSpec extensionMethod = new MethodExtensionPointSpec();
        extensionMethod.setCode(extensionAnnotation.code());
        extensionMethod.setName(extensionAnnotation.name());
        extensionMethod.setDescription(extensionAnnotation.description());
        extensionMethod.setGroup(extensionAnnotation.group());
        extensionMethod.setReducePolicy(extensionAnnotation.reducePolicy());
        extensionMethod.setMethod(method);
        extensionMethod.setExtensionPointClass(extensionPointClass);
        return extensionMethod;
    }

    public Method getMethod() {
        return method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

}
