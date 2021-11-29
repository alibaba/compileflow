package com.alibaba.compileflow.engine.common.extension.invoker;

/**
 * @author yusu
 */
public interface MethodInvoker {

    Object invoke(Object... args) throws Exception;

}
