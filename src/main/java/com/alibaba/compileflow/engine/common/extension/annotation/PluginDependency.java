package com.alibaba.compileflow.engine.common.extension.annotation;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author yusu
 */
@Retention(RUNTIME)
@Target({TYPE})
@Repeatable(PluginDependsOn.class)
public @interface PluginDependency {

    String pluginId();

    String pluginVersion() default "";

}
