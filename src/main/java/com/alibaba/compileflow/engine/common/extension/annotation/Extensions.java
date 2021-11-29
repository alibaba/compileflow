package com.alibaba.compileflow.engine.common.extension.annotation;

import com.alibaba.compileflow.engine.common.extension.consts.ExtensionConsts;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author yusu
 */
@Retention(RUNTIME)
@Target({TYPE})
public @interface Extensions {

    int priority() default 500;

    String scenario() default ExtensionConsts.DEFAULT_SCENARIO;

}
