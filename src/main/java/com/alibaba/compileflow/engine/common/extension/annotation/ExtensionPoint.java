package com.alibaba.compileflow.engine.common.extension.annotation;

import com.alibaba.compileflow.engine.common.extension.constant.ReducePolicy;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
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
