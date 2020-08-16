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
package com.alibaba.compileflow.engine.process.preruntime.validator;

/**
 * @author yusu
 */
public class ValidateMessage {

    private boolean success;

    private String message;

    private ValidateMessage(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    private static ValidateMessage of(boolean success, String message) {
        return new ValidateMessage(success, message);
    }

    public static ValidateMessage success(String message) {
        return ValidateMessage.of(true, message);
    }

    public static ValidateMessage fail(String message) {
        return ValidateMessage.of(false, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isFail() {
        return !success;
    }

    public String getMessage() {
        return message;
    }

}
