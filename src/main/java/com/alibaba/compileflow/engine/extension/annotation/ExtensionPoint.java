package com.alibaba.compileflow.engine.extension.annotation;

import com.alibaba.compileflow.engine.extension.constant.ReducePolicy;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 标记一个接口或方法为扩展点，供插件或扩展实现。
 * <p>
 * 用法示例：
 * <pre>
 * &#64;ExtensionPoint(
 *     code = "myService",
 *     name = "我的服务扩展点",
 *     description = "用于扩展自定义服务",
 *     group = "service",
 *     reducePolicy = ReducePolicy.FIRST
 * )
 * public interface MyService { ... }
 * </pre>
 * <p>
 * 参数说明：
 * <ul>
 *   <li>code：扩展点唯一标识，必填。</li>
 *   <li>name：扩展点名称，便于管理，可选。</li>
 *   <li>description：扩展点描述，可选。</li>
 *   <li>group：扩展点分组，便于分类管理，可选。</li>
 *   <li>reducePolicy：扩展点聚合策略，决定多实现时的处理方式，必填。</li>
 * </ul>
 * <p>
 * 扩展点会被 ExtensionManager 自动扫描和注册，扩展实现需通过 @ExtensionRealization 注解标记。
 * @author yusu
 */
@Retention(RUNTIME)
@Target({TYPE, METHOD})
@Inherited
public @interface ExtensionPoint {

    String code();

    String name() default "";

    String description() default "";

    String group() default "#";

    ReducePolicy reducePolicy();

}
