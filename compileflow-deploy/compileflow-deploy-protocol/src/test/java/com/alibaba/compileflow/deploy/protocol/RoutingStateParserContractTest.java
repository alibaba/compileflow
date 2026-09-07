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
package com.alibaba.compileflow.deploy.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.engine.spi.routing.AliasTargeting;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoutingStateParserContractTest {
    private static final long UPDATED_AT = 1_700_000_000_000L;

    private static void assertInvalid(String payload) {
        assertThatThrownBy(() -> RoutingStateCodec.parse(payload)).isInstanceOf(DeploymentException.class);
    }

    @Test
    void rejectsMissingAndMalformedPayloads() {
        assertThat(RoutingStateCodec.parse(null)).isNull();
        assertThat(RoutingStateCodec.parse("  ")).isNull();
        assertThatThrownBy(() -> RoutingStateCodec.parse("not-json")).hasMessageContaining("JSON object");
        assertThatThrownBy(() -> RoutingStateCodec.parse("[]")).hasMessageContaining("JSON object");
        assertThatThrownBy(() -> RoutingStateCodec.parse("{}")).hasMessageContaining("must not be empty");
    }

    @Test
    void roundTripsCompleteCanaryState() {
        AliasTargeting targeting = new AliasTargeting("enterprise-cohort", Map.of("region", "eu"));
        String json = RoutingStateCodec.aliasStateJson("staging", "order.flow", "prod", "v2", "v3", 2_000, targeting,
                42L, "alice", UPDATED_AT);

        RoutingStateUpdate update = RoutingStateCodec.parse(json);

        assertThat(update).isNotNull();
        assertThat(update.getNamespace()).isEqualTo("staging");
        assertThat(update.getCode()).isEqualTo("order.flow");
        assertThat(update.getAlias()).isEqualTo("prod");
        assertThat(update.getStableVersion()).isEqualTo("v2");
        assertThat(update.getCandidateVersion()).isEqualTo("v3");
        assertThat(update.getCandidateWeightBps()).isEqualTo(2_000);
        assertThat(update.getTargeting()).isEqualTo(targeting);
        assertThat(update.isDeleted()).isFalse();
        assertThat(update.getActor()).isEqualTo("alice");
        assertThat(update.getUpdatedAt()).isEqualTo(UPDATED_AT);
        assertThat(update.getAliasRevision()).isEqualTo(42L);
    }

    @Test
    void preservesDeletionAsTheSameResourceKind() {
        String json = RoutingStateCodec.aliasTombstoneJson("default", "order.flow", "prod", 7L, "system", UPDATED_AT);

        RoutingStateUpdate update = RoutingStateCodec.parse(json);

        assertThat(update).isNotNull();
        assertThat(update.isDeleted()).isTrue();
        assertThat(update.getStableVersion()).isNull();
        assertThat(update.getCandidateVersion()).isNull();
        assertThat(update.getCandidateWeightBps()).isZero();
        assertThat(update.getAliasRevision()).isEqualTo(7L);
    }

    @Test
    void rejectsIncompleteEnvelope() {
        assertInvalid(payloadWithout("schemaVersion"));
        assertInvalid(payloadWithout("kind"));
        assertInvalid(payloadWithout("namespace"));
        assertInvalid(payloadWithout("code"));
        assertInvalid(payloadWithout("alias"));
        assertInvalid(payloadWithout("stableVersion"));
        assertInvalid(payloadWithout("revision"));
        assertInvalid(payloadWithout("actor"));
        assertInvalid(payloadWithout("updatedAt"));
    }

    @Test
    void rejectsUnknownSchemaKindAndFields() {
        assertInvalid(validPayload().replace("\"schemaVersion\":1", "\"schemaVersion\":2"));
        assertInvalid(validPayload().replace("\"aliasState\"", "\"unknown\""));
        assertInvalid(validPayload()
            .replace("\"updatedAt\":" + UPDATED_AT, "\"unknown\":true,\"updatedAt\":" + UPDATED_AT));
        assertInvalid(validPayload().replace("\"namespace\":\"default\"", "\"namespace\":\"\\u00a0default\""));
    }

    @Test
    void rejectsDuplicateFieldsAndTrailingJson() {
        assertInvalid(validPayload().replace("\"revision\":1", "\"revision\":1,\"revision\":2"));
        assertInvalid(validPayload() + "{}");
    }

    @Test
    void rejectsNonIntegralOrNonPositiveOrderingMetadata() {
        assertInvalid(validPayload().replace("\"revision\":1", "\"revision\":0"));
        assertInvalid(validPayload().replace("\"revision\":1", "\"revision\":1.5"));
        assertInvalid(validPayload().replace("\"revision\":1", "\"revision\":\"1\""));
        assertInvalid(validPayload().replace("\"updatedAt\":" + UPDATED_AT, "\"updatedAt\":0"));
    }

    @Test
    void rejectsIncompleteOrInvalidCandidateState() {
        assertInvalid(validPayload()
            .replace("\"stableVersion\":\"v1\"", "\"stableVersion\":\"v1\",\"candidateVersion\":\"v2\""));
        assertInvalid(validPayload()
            .replace("\"stableVersion\":\"v1\"", "\"stableVersion\":\"v1\",\"candidateWeightBps\":500"));
        assertInvalid(validPayload()
            .replace("\"stableVersion\":\"v1\"",
                    "\"stableVersion\":\"v1\",\"candidateVersion\":\"v1\"," + "\"candidateWeightBps\":500"));
        assertInvalid(validPayload()
            .replace("\"stableVersion\":\"v1\"",
                    "\"stableVersion\":\"v1\",\"candidateVersion\":\"v2\"," + "\"candidateWeightBps\":10000"));
        assertInvalid(validPayload()
            .replace("\"stableVersion\":\"v1\"", "\"stableVersion\":\"v1\",\"candidateVersion\":null"));
        assertInvalid(validPayload().replace("\"revision\":1", "\"deleted\":null,\"revision\":1"));
        assertInvalid(validPayload()
            .replace("\"stableVersion\":\"v1\"",
                    "\\\"stableVersion\\\":\\\"v1\\\",\\\"targetingPolicy\\\":" + "\\\"enterprise-cohort\\\""));
    }

    @Test
    void rejectsRouteTargetsOnTombstone() {
        assertInvalid(validPayload()
            .replace("\"stableVersion\":\"v1\"", "\"deleted\":true,\"stableVersion\":" + "\"v1\""));
    }

    private String payloadWithout(String field) {
        return validPayload().replaceAll(",?\"" + field + "\":(?:\"[^\"]*\"|-?[0-9]+)", "");
    }

    private String validPayload() {
        return "{\"schemaVersion\":1,\"kind\":\"aliasState\"," + "\"namespace\":\"default\",\"code\":\"order.flow\","
                + "\"alias\":\"prod\",\"stableVersion\":\"v1\"," + "\"revision\":1,\"actor\":\"system\","
                + "\"updatedAt\":" + UPDATED_AT + "}";
    }
}
