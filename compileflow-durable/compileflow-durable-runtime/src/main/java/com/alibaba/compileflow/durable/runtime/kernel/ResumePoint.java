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
package com.alibaba.compileflow.durable.runtime.kernel;

import com.alibaba.compileflow.durable.api.validation.DurableIdentifiers;
import java.util.Objects;

/**
 * Process-semantic coordinate from which the next bounded Machine Turn is derived.
 *
 * @author yusu
 */
public record ResumePoint(Kind kind, String elementId) {
    public ResumePoint {
        kind = Objects.requireNonNull(kind, "kind");
        if (kind == Kind.START) {
            if (elementId != null) {
                throw new IllegalArgumentException("START must not declare elementId");
            }
        } else {
            elementId = requireText(elementId);
        }
    }

    public static ResumePoint start() {
        return new ResumePoint(Kind.START, null);
    }

    /**
     * The next Turn must begin by executing this element.
     */
    public static ResumePoint beforeElement(String elementId) {
        return new ResumePoint(Kind.BEFORE_ELEMENT, elementId);
    }

    /**
     * The next Turn executes one already-admitted operation-level iteration body.
     */
    public static ResumePoint beforeIterationBody(String elementId) {
        return new ResumePoint(Kind.BEFORE_ITERATION_BODY, elementId);
    }

    /**
     * The next Turn must first consume the committed occurrence issued by this element.
     */
    public static ResumePoint afterElement(String elementId) {
        return new ResumePoint(Kind.AFTER_ELEMENT, elementId);
    }

    /**
     * This branch reached its structured join and is parked until every sibling arrives.
     */
    public static ResumePoint atJoin(String elementId) {
        return new ResumePoint(Kind.AT_JOIN, elementId);
    }

    /**
     * Runtime dispatch key derived solely from Process semantics.
     */
    public String key() {
        return switch (kind) {
            case START -> "START";
            case BEFORE_ELEMENT -> "BEFORE_ELEMENT:" + elementId;
            case BEFORE_ITERATION_BODY -> "BEFORE_ITERATION_BODY:" + elementId;
            case AFTER_ELEMENT -> "AFTER_ELEMENT:" + elementId;
            case AT_JOIN -> "AT_JOIN:" + elementId;
        };
    }

    public boolean isStart() {
        return kind == Kind.START;
    }

    public boolean isBeforeElement() {
        return kind == Kind.BEFORE_ELEMENT;
    }

    public boolean isBeforeIterationBody() {
        return kind == Kind.BEFORE_ITERATION_BODY;
    }

    public boolean isAfterElement() {
        return kind == Kind.AFTER_ELEMENT;
    }

    public boolean isAtJoin() {
        return kind == Kind.AT_JOIN;
    }

    private static String requireText(String value) {
        return DurableIdentifiers.requireIdentity(value, "elementId", 128);
    }

    public enum Kind {
        START,
        BEFORE_ELEMENT,
        BEFORE_ITERATION_BODY,
        AFTER_ELEMENT,
        AT_JOIN
    }
}
