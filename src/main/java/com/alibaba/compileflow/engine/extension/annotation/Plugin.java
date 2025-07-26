package com.alibaba.compileflow.engine.extension.annotation;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author yusu
 */
@Retention(RUNTIME)
@Target({TYPE})
@Inherited
public @interface Plugin {

    String id();

    String version();

    String name() default "";

    String description() default "";

    String helpLink() default "";

    PluginDependsOn dependsOn() default @PluginDependsOn;

}
