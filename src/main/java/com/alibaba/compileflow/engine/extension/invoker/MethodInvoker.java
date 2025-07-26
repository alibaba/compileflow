package com.alibaba.compileflow.engine.extension.invoker;

/**
 * @author yusu
 */
public interface MethodInvoker {

    Object invoke(Object... args) throws Exception;

}
