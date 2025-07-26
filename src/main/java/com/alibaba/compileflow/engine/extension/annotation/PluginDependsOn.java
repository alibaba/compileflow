package com.alibaba.compileflow.engine.extension.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author yusu
 */
@Retention(RUNTIME)
@Target({TYPE})
public @interface PluginDependsOn {

    PluginDependency[] value() default {};

}
