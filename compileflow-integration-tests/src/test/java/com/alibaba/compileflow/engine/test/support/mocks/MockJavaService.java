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
package com.alibaba.compileflow.engine.test.support.mocks;

import org.springframework.stereotype.Service;

@Service
public class MockJavaService {
    // ==================== Arithmetic Operations ====================
    public Integer add(Integer a, Integer b) {
        if (a == null) {
            a = 0;
        }
        if (b == null) {
            b = 0;
        }
        return a + b;
    }

    public Integer multiply(Integer a, Integer b) {
        if (a == null) {
            a = 0;
        }
        if (b == null) {
            b = 0;
        }
        return a * b;
    }

    public Integer divide(Integer a, Integer b) {
        if (a == null) {
            a = 0;
        }
        if (b == null) {
            b = 0;
        }
        return a / b;
    }

    public Integer validationSuccessCount(Integer value, Integer threshold) {
        return value != null && value >= threshold ? 1 : 0;
    }

    public Integer validationFailureCount(Integer value, Integer threshold) {
        return value == null || value < threshold ? 1 : 0;
    }

    public String formatValidationResult(String prefix, Integer sum, Integer ok, Integer fail) {
        return prefix + "sum=" + sum + ", ok=" + (ok == null ? 0 : ok) + ", fail=" + (fail == null ? 0 : fail);
    }

    // ==================== Order Processing ====================
    public String processOrder(String orderId) {
        return "order_" + orderId;
    }

    public String processRoute(String route) {
        return "route_" + route;
    }

    public String processTask(String task) {
        return "task_" + task;
    }

    // ==================== Step Accumulation ====================
    public String appendStep(String path, String step) {
        if (path == null || path.isEmpty()) {
            return step;
        }
        return path + "->" + step;
    }

    public String appendStep(String path) {
        return appendStep(path, "step");
    }

    public String finalizeResult(String path) {
        return "completed_" + path;
    }

    // ==================== String Processing ====================
    public void echo(String value) {}

    public void echo(Integer value) {}

    public void echo(String value, Integer num) {}

    public String processString(String input) {
        return "processed_" + input;
    }

    public String processString(Boolean input) {
        return "processed_" + input;
    }

    public String processStep(String input) {
        return input;
    }

    // ==================== Result Combination ====================
    public String combineResults(String payment, String delivery) {
        return "combined_" + payment + "_" + delivery;
    }

    public String combineResults(String payment, Integer delivery) {
        return "combined_" + payment + "_" + delivery;
    }

    // ==================== Price Calculation ====================
    public int calculatePrice(int num) {
        return 30 * num;
    }

    public int calculatePriceForHotDeploy(int num) {
        return 1000 * num;
    }

    // ==================== Utility Methods ====================
    public int getMockReturnValue(int num) {
        return num - 100;
    }

    public void logWaitPaymentTask() {}
}
