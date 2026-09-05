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
package com.alibaba.compileflow.engine.preflight;

import com.alibaba.compileflow.engine.ProcessIdentifiers;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable report produced by preflight validation.
 *
 * <p>The report identifies the local definition being checked, summarizes the overall
 * outcome, and records lint and compilation stage results. Publication namespace and
 * version belong to the version publication command, not to this local source report.
 *
 * @author yusu
 */
public final class ProcessPreflightReport {
    /**
     * Serialized as process code.
     */
    private final String code;
    /**
     * Serialized as aggregate preflight outcome.
     */
    private final OverallStatus overallStatus;
    /**
     * Serialized as total preflight duration in milliseconds.
     */
    private final long totalDurationMs;
    /**
     * Serialized as immutable per-stage result list.
     */
    private final List<Item> items;

    private ProcessPreflightReport(Builder builder) {
        this.code = ProcessIdentifiers.requireCode(builder.code);
        if (builder.totalDurationMs < 0L) {
            throw new IllegalArgumentException("totalDurationMs must not be negative");
        }
        if (builder.items.isEmpty()) {
            throw new IllegalArgumentException("Preflight report must contain at least one item");
        }
        this.totalDurationMs = builder.totalDurationMs;
        this.items = List.copyOf(builder.items);
        this.overallStatus = items
            .stream()
            .anyMatch(item -> item.status == ItemStatus.FAIL || item.status == ItemStatus.TIMEOUT)
                ? OverallStatus.FAIL
                : OverallStatus.PASS;
    }

    /**
     * Creates a report builder.
     *
     * @return new report builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the process code.
     *
     * @return non-blank process code
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns the overall preflight status.
     *
     * @return overall status
     */
    public OverallStatus getOverallStatus() {
        return overallStatus;
    }

    /**
     * Returns total preflight duration.
     *
     * @return duration in milliseconds
     */
    public long getTotalDurationMs() {
        return totalDurationMs;
    }

    /**
     * Returns immutable per-stage preflight items.
     *
     * @return immutable item list
     */
    public List<Item> getItems() {
        return items;
    }

    /**
     * Overall preflight outcome.
     */
    public enum OverallStatus {
        /**
         * Every required preflight stage passed.
         */
        PASS,
        /**
         * At least one required preflight stage failed.
         */
        FAIL
    }

    /**
     * Type of individual preflight item.
     */
    public enum ItemType {
        /**
         * Static process-definition linting.
         */
        LINT,
        /**
         * Generated Java compilation.
         */
        COMPILE
    }

    /**
     * Status of an individual preflight item.
     */
    public enum ItemStatus {
        /**
         * Stage completed successfully.
         */
        PASS,
        /**
         * Stage completed with a validation failure.
         */
        FAIL,
        /**
         * Stage exceeded its configured deadline.
         */
        TIMEOUT
    }

    /**
     * Result item for one preflight stage.
     */
    public static final class Item {
        /**
         * Serialized as preflight stage type.
         */
        private final ItemType type;
        /**
         * Serialized as stage outcome.
         */
        private final ItemStatus status;
        /**
         * Serialized as stage duration in milliseconds.
         */
        private final long durationMs;
        /**
         * Serialized as human-readable stage message.
         */
        private final String message;

        private Item(ItemType type, ItemStatus status, long durationMs, String message) {
            this.type = Objects.requireNonNull(type, "type");
            this.status = Objects.requireNonNull(status, "status");
            if (durationMs < 0L) {
                throw new IllegalArgumentException("Item durationMs must not be negative");
            }
            this.durationMs = durationMs;
            this.message = message;
        }

        /**
         * Returns the preflight item type.
         *
         * @return item type
         */
        public ItemType getType() {
            return type;
        }

        /**
         * Returns the preflight item status.
         *
         * @return item status
         */
        public ItemStatus getStatus() {
            return status;
        }

        /**
         * Returns item execution duration.
         *
         * @return duration in milliseconds
         */
        public long getDurationMs() {
            return durationMs;
        }

        /**
         * Returns a human-readable item message.
         *
         * @return item message, or {@code null} when not provided
         */
        public String getMessage() {
            return message;
        }
    }

    /**
     * Builder for immutable {@link ProcessPreflightReport} instances.
     */
    public static final class Builder {
        private final List<Item> items = new ArrayList<>();
        private String code;
        private long totalDurationMs;

        private Builder() {
        }

        /**
         * Sets the process code.
         *
         * @param code process code
         * @return this builder
         */
        public Builder code(String code) {
            this.code = code;
            return this;
        }

        /**
         * Sets the total preflight duration.
         *
         * @param ms duration in milliseconds
         * @return this builder
         */
        public Builder totalDurationMs(long ms) {
            this.totalDurationMs = ms;
            return this;
        }

        /**
         * Adds a preflight stage item.
         *
         * @param type       item type
         * @param status     item status
         * @param durationMs item duration in milliseconds
         * @param message    human-readable item message
         * @return this builder
         */
        public Builder addItem(ItemType type, ItemStatus status, long durationMs, String message) {
            this.items.add(new Item(type, status, durationMs, message));
            return this;
        }

        /**
         * Builds the immutable report.
         *
         * @return preflight report
         */
        public ProcessPreflightReport build() {
            return new ProcessPreflightReport(this);
        }
    }
}
