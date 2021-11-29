package com.alibaba.compileflow.engine.common.extension.spec;

import com.alibaba.compileflow.engine.common.extension.ExtensionPoint;
import com.alibaba.compileflow.engine.common.extension.annotation.Extension;

/**
 * @author yusu
 */
public class ClassExtensionPointSpec extends ExtensionPointSpec {

    public static ClassExtensionPointSpec of(Extension extensionAnnotation,
                                             Class<? extends ExtensionPoint> extensionPointClass) {
        ClassExtensionPointSpec extensionMethod = new ClassExtensionPointSpec();
        extensionMethod.setCode(extensionAnnotation.code());
        extensionMethod.setName(extensionAnnotation.name());
        extensionMethod.setDescription(extensionAnnotation.description());
        extensionMethod.setGroup(extensionAnnotation.group());
        extensionMethod.setReducePolicy(extensionAnnotation.reducePolicy());
        extensionMethod.setExtensionPointClass(extensionPointClass);
        return extensionMethod;
    }

}
