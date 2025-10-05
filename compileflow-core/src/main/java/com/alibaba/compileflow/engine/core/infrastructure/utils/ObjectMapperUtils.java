package com.alibaba.compileflow.engine.core.infrastructure.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;

/**
 * @author yusu
 */
public final class ObjectMapperUtils {

    public static ObjectMapper jacksonMapper() {
        ObjectMapper mapper = JsonMapper.builder().build();
        try {
            mapper.registerModule(new AfterburnerModule());
        } catch (Throwable ignore) {
        }
        return mapper;
    }

}
