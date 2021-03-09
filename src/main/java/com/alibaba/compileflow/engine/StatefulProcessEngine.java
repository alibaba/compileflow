package com.alibaba.compileflow.engine;

import java.util.Map;

/**
 * @author wuxiang
 * @author yusu
 */
public interface StatefulProcessEngine<T extends FlowModel> extends ProcessEngine<T> {

    /**
     * @param code
     * @param currentTag
     * @param context
     */
    Map<String, Object> trigger(String code, String currentTag, Map<String, Object> context);

}