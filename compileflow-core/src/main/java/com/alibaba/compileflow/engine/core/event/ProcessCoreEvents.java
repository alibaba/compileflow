/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.core.event;

/**
 * Defines core process event types.
 *
 * @author yusu
 */
public final class ProcessCoreEvents {


    public static class ExecutionStarted extends ProcessEvent.AbstractProcessEvent {
        public ExecutionStarted(String processCode, ProcessEventContext context) {
            super(ProcessEvent.EventType.EXECUTION_STARTED, processCode, context);
        }
    }

    public static class ExecutionCompleted extends ProcessEvent.AbstractProcessEvent {
        public ExecutionCompleted(String processCode, ProcessEventContext context) {
            super(ProcessEvent.EventType.EXECUTION_COMPLETED, processCode, context);
        }
    }

    public static class ExecutionFailed extends ProcessEvent.AbstractProcessEvent {
        public ExecutionFailed(String processCode, ProcessEventContext context) {
            super(ProcessEvent.EventType.EXECUTION_FAILED, processCode, context);
        }
    }

    public static class TriggerStarted extends ProcessEvent.AbstractProcessEvent {
        private final String tag;
        private final String event;

        public TriggerStarted(String processCode, String tag, String event, ProcessEventContext context) {
            super(ProcessEvent.EventType.TRIGGER_STARTED, processCode, context);
            this.tag = tag;
            this.event = event;
        }

        public String getTag() {
            return tag;
        }

        public String getEvent() {
            return event;
        }
    }

    public static class TriggerCompleted extends ProcessEvent.AbstractProcessEvent {
        private final String tag;
        private final String event;

        public TriggerCompleted(String processCode, String tag, String event, ProcessEventContext context) {
            super(ProcessEvent.EventType.TRIGGER_COMPLETED, processCode, context);
            this.tag = tag;
            this.event = event;
        }

        public String getTag() {
            return tag;
        }

        public String getEvent() {
            return event;
        }
    }

    public static class TriggerFailed extends ProcessEvent.AbstractProcessEvent {
        private final String tag;
        private final String event;

        public TriggerFailed(String processCode, String tag, String event, ProcessEventContext context) {
            super(ProcessEvent.EventType.TRIGGER_FAILED, processCode, context);
            this.tag = tag;
            this.event = event;
        }

        public String getTag() {
            return tag;
        }

        public String getEvent() {
            return event;
        }
    }


    public static class CompilationCompleted extends ProcessEvent.AbstractProcessEvent {
        private final String[] codes;

        public CompilationCompleted(String[] codes, ProcessEventContext context) {
            super(ProcessEvent.EventType.COMPILATION_COMPLETED,
                    codes.length == 1 ? codes[0] : "batch-" + codes.length, context);
            this.codes = codes.clone();
        }

        public String[] getCodes() {
            return codes.clone();
        }

        public int getCount() {
            return codes.length;
        }
    }

    public static class CompilationFailed extends ProcessEvent.AbstractProcessEvent {
        private final String[] codes;

        public CompilationFailed(String[] codes, ProcessEventContext context) {
            super(ProcessEvent.EventType.COMPILATION_FAILED,
                    codes.length == 1 ? codes[0] : "batch-" + codes.length, context);
            this.codes = codes.clone();
        }

        public String[] getCodes() {
            return codes.clone();
        }

        public int getCount() {
            return codes.length;
        }

    }

}
