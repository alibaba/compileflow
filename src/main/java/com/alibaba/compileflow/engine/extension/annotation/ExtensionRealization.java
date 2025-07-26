package com.alibaba.compileflow.engine.extension.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 标记一个类为扩展点的具体实现。
 * <p>
 * 参数说明：
 * <ul>
 *   <li>priority：扩展实现优先级，数值越大优先级越高，默认500。</li>
 *   <li>scenario：扩展实现适用场景，默认"#"。</li>
 * </ul>
 * <p>
 * 扩展实现会被 ExtensionManager 自动扫描和注册到对应扩展点。
 */
@Retention(RUNTIME)
@Target({TYPE})
public @interface ExtensionRealization {

    int priority() default 500;

    String scenario() default "#";

}
