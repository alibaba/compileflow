/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.workbench.server.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/**
 * Enforces the JSON type contract at the server boundary.
 *
 * @author yusu
 */
@Configuration(proxyBeanMethods = false)
public class ServerJacksonConfiguration {
    /**
     * Rejects unknown fields and lossy or implicit scalar conversions.
     *
     * @return final JSON mapper customization
     */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public JsonMapperBuilderCustomizer strictJsonMapperBuilderCustomizer() {
        return builder -> builder
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .withCoercionConfig(LogicalType.Textual, coercion -> {
                coercion.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
                coercion.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
                coercion.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
            });
    }
}
