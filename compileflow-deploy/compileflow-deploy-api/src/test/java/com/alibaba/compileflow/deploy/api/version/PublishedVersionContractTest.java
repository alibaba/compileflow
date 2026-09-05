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
package com.alibaba.compileflow.deploy.api.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class PublishedVersionContractTest {
    @Test
    void queryNormalizesIdentityPrefixAndCarriesTypedCursor() {
        PublishedVersionCursor cursor = new PublishedVersionCursor("opaque-cursor");
        PublishedVersionQuery query =
                new PublishedVersionQuery(ProcessRef.DEFAULT_NAMESPACE, "order.approve", " r-2026 ", cursor, 100);

        assertThat(query.getNamespace()).isEqualTo(ProcessRef.DEFAULT_NAMESPACE);
        assertThat(query.getCode()).isEqualTo("order.approve");
        assertThat(query.getVersionPrefix()).isEqualTo("r-2026");
        assertThat(query.getCursor()).isSameAs(cursor);
        assertThat(query.getLimit()).isEqualTo(100);
    }

    @Test
    void queryRejectsInvalidBounds() {
        assertThatThrownBy(() -> new PublishedVersionQuery(ProcessRef.DEFAULT_NAMESPACE, "order.approve", null, null, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("limit must be between 1 and 100");
        assertThatThrownBy(() -> new PublishedVersionQuery(ProcessRef.DEFAULT_NAMESPACE, "order.approve", null, null,
                101))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("limit must be between 1 and 100");
        assertThatThrownBy(() -> new PublishedVersionQuery(ProcessRef.DEFAULT_NAMESPACE, "order.approve", "release%",
                null, 20))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("version must start with an ASCII letter or digit");
        assertThatThrownBy(() -> new PublishedVersionQuery("", "order.approve", null, null, 20))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("namespace");
        assertThatThrownBy(() -> new PublishedVersionCursor(" next "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Base64 URL token");
    }

    @Test
    void pageDefensivelyCopiesVersionsAndValidatesShape() {
        PublishedProcessVersion version = new PublishedProcessVersion(ProcessRef.version("default", "order.approve",
                        "r-1"), ProcessModelType.TBBPM, "a".repeat(64), Collections.emptyMap(), "release-service",
                Instant.ofEpochMilli(1L));
        PublishedVersionCursor nextCursor = new PublishedVersionCursor("next");
        PublishedVersionPage page = new PublishedVersionPage(List.of(version), nextCursor);

        assertThat(page.getVersions()).containsExactly(version);
        assertThat(page.getNextCursor()).isSameAs(nextCursor);
        assertThat(page.hasMore()).isTrue();
        assertThatThrownBy(() -> page.getVersions().add(version)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new PublishedVersionPage(List.of(), nextCursor)).isInstanceOf(
                IllegalArgumentException.class);
    }

    @Test
    void versionDefensivelyCopiesMetadata() {
        ProcessRef.Version owner = ProcessRef.version("default", "order.approve", "r-1");
        java.util.Map<String, String> metadata = new java.util.HashMap<>();
        metadata.put("release", "stable");

        PublishedProcessVersion version = new PublishedProcessVersion(owner, ProcessModelType.TBBPM, "a".repeat(64),
                metadata, "release-service", Instant.ofEpochMilli(1L));
        metadata.clear();

        assertThat(version.getMetadata()).containsExactlyEntriesOf(java.util.Map.of("release", "stable"));
        assertThatThrownBy(() -> version.getMetadata().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
