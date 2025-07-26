package com.alibaba.compileflow.engine.extension.invoker;

import org.springframework.cglib.reflect.FastClass;
import org.springframework.cglib.reflect.FastMethod;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author yusu
 */
public class FastMethodInvoker implements MethodInvoker {

    private final FastMethod fastMethod;
    private final Object targetObject;

    private FastMethodInvoker(Object targetObject, Method method) {
        FastClass fastClass = FastClass.create(targetObject.getClass());
        this.fastMethod = fastClass.getMethod(method);
        this.targetObject = targetObject;
    }

    public static FastMethodInvoker of(Object targetObject, Method method) {
        return new FastMethodInvoker(targetObject, method);
    }

    @Override
    public Object invoke(Object... args) throws InvocationTargetException {
        return fastMethod.invoke(targetObject, args);
    }

}
