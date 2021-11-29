package com.alibaba.compileflow.engine.common.extension.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author yusu
 */
@Retention(RUNTIME)
@Target({TYPE})
public @interface Extension {

    int priority() default 500;

    String scenario() default "#";

}
