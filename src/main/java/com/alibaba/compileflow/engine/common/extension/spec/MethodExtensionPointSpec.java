package com.alibaba.compileflow.engine.common.extension.spec;

import com.alibaba.compileflow.engine.common.extension.IExtensionPoint;
import com.alibaba.compileflow.engine.common.extension.annotation.ExtensionPoint;

import java.lang.reflect.Method;

/**
 * @author yusu
 */
public class MethodExtensionPointSpec extends ExtensionPointSpec {

    private Method method;

    public static MethodExtensionPointSpec of(ExtensionPoint extensionPointAnnotation,
                                              Class<? extends IExtensionPoint> extensionPointClass,
                                              Method method) {
        MethodExtensionPointSpec extensionMethod = new MethodExtensionPointSpec();
        extensionMethod.setCode(extensionPointAnnotation.code());
        extensionMethod.setName(extensionPointAnnotation.name());
        extensionMethod.setDescription(extensionPointAnnotation.description());
        extensionMethod.setGroup(extensionPointAnnotation.group());
        extensionMethod.setReducePolicy(extensionPointAnnotation.reducePolicy());
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
