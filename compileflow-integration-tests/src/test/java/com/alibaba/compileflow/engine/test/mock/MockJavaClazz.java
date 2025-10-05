package com.alibaba.compileflow.engine.test.mock;

import org.springframework.stereotype.Service;

/**
 * @author pin
 */
@Service
public class MockJavaClazz {

    public void mockJavaMethod(int num) {
        System.out.println("java: number is " + num);
    }

    public void mockJavaMethod(long num) {
        System.out.println("spring-bean: number is " + num);
    }

    public int mockReturnMethod(int num) {
        System.out.println("java: minus 100");
        return num - 100;
    }

    public void doException() {
        throw new RuntimeException("mock exception");
    }

    public void mockMonitorMethod(Throwable e) {
        if (e != null) {
            e.printStackTrace();
        }
    }

    public int calPrice(int num) {
        System.out.println("total price: " + 30 * num);
        return 30 * num;
    }

    public int calPriceV2(int num) {
        System.out.println("total price: " + 1000 * num);
        return 1000 * num;
    }

    public Integer add(Integer a, Integer b) {
        if (a == null) a = 0;
        if (b == null) b = 0;
        System.out.println("a + b = " + (a + b));
        return a + b;
    }

    public Integer multiply(Integer a, Integer b) {
        if (a == null) a = 0;
        if (b == null) b = 0;
        System.out.println("a * b = " + (a * b));
        return a * b;
    }

    public Integer divide(Integer a, Integer b) {
        if (a == null) a = 0;
        if (b == null) b = 0;
        System.out.println("a / b = " + (a / b));
        return a / b;
    }

    public void echo(String value) {
        System.out.println("echo: " + value);
    }

    public void echo(Integer value) {
        System.out.println("echo: " + value);
    }

    public void echo(String value, Integer num) {
        System.out.println("echo: " + value + ", " + num);
    }

    public void printWaitPaymentTask() {
        System.out.println("wait payment task");
    }

    public String echoResult(String value) {
        System.out.println("echo: " + value);
        return value;
    }

    /**
     * Process user task result - for UserTask testing
     */
    public String processUserTaskResult(String taskResult) {
        System.out.println("Processing user task result: " + taskResult);
        return "processed_" + taskResult;
    }

    /**
     * Process string input - for InclusiveGateway testing
     */
    public String processString(String input) {
        System.out.println("Processing string: " + input);
        return "processed_" + input;
    }

    public String processString(Boolean input) {
        System.out.println("Processing string: " + input);
        return "processed_" + input;
    }

    /**
     * Simulate failure for error handling tests
     */
    public void simulateFailure() {
        throw new RuntimeException("Simulated failure for testing");
    }

    /**
     * Method that takes time for performance testing
     */
    public String slowMethod(String input) throws InterruptedException {
        Thread.sleep(100); // Simulate slow operation
        return "slow_" + input;
    }

    /**
     * Calculate discount based on amount and customer type - for BusinessRuleTask testing
     */
    public Double calculateDiscount(Integer amount, String customerType) {
        System.out.println("Calculating discount for amount: " + amount + ", customerType: " + customerType);
        if ("VIP".equals(customerType)) {
            return 0.2; // 20% discount for VIP
        } else if ("REGULAR".equals(customerType)) {
            return 0.1; // 10% discount for regular
        } else {
            return 0.0; // No discount
        }
    }

    /**
     * Apply discount to amount - for BusinessRuleTask testing
     */
    public Integer applyDiscount(Integer amount, Double discount) {
        if (amount == null) amount = 0;
        if (discount == null) discount = 0.0;
        int finalAmount = (int) (amount * (1 - discount));
        System.out.println("Applied discount " + discount + " to amount " + amount + ", result: " + finalAmount);
        return finalAmount;
    }

    /**
     * Validate business rule - for BusinessRuleTask testing
     */
    public Boolean validateBusinessRule(String ruleType, Object value) {
        System.out.println("Validating business rule: " + ruleType + " with value: " + value);
        return true; // Always pass for testing
    }

    /**
     * Process timer event - for TimerEvent testing
     */
    public String processTimerEvent(String eventData) {
        System.out.println("Processing timer event: " + eventData);
        return "timer_processed_" + eventData;
    }

    /**
     * Process message event - for MessageEvent testing
     */
    public String processMessageEvent(String messageData) {
        System.out.println("Processing message event: " + messageData);
        return "message_processed_" + messageData;
    }

    /**
     * Process signal event - for SignalEvent testing
     */
    public String processSignalEvent(String signalData) {
        System.out.println("Processing signal event: " + signalData);
        return "signal_processed_" + signalData;
    }

    /**
     * Process error event - for ErrorEvent testing
     */
    public String processErrorEvent(String errorData) {
        System.out.println("Processing error event: " + errorData);
        if ("TRIGGER_ERROR".equals(errorData)) {
            throw new RuntimeException("Simulated error for testing");
        }
        return "error_processed_" + errorData;
    }

    /**
     * Combine two results - for stateful testing
     */
    public String combineResults(String payment, String delivery) {
        System.out.println("Combining results: payment=" + payment + ", delivery=" + delivery);
        return "combined_" + payment + "_" + delivery;
    }

    public String combineResults(String payment, Integer delivery) {
        System.out.println("Combining results: payment=" + payment + ", delivery=" + delivery);
        return "combined_" + payment + "_" + delivery;
    }

    /**
     * Process approval result - for stateful approval testing
     */
    public String processApprovalResult(String approvalData, String approvalType) {
        System.out.println("Processing approval: type=" + approvalType + ", data=" + approvalData);
        if ("REJECT".equals(approvalType)) {
            return "rejected_" + approvalData;
        }
        return "approved_" + approvalData;
    }

    /**
     * Simulate waiting task processing - for waitTask testing
     */
    public String processWaitingTask(String taskData) {
        System.out.println("Processing waiting task: " + taskData);
        return "wait_processed_" + taskData;
    }

    /**
     * Simulate event trigger processing - for waitEventTask testing
     */
    public String processEventTrigger(String eventData, String eventType) {
        System.out.println("Processing event trigger: type=" + eventType + ", data=" + eventData);
        return "event_" + eventType + "_processed_" + eventData;
    }

    /**
     * Validate stateful condition - for stateful gateway testing
     */
    public Boolean validateStatefulCondition(String conditionData, Integer threshold) {
        System.out.println("Validating stateful condition: data=" + conditionData + ", threshold=" + threshold);
        if (conditionData != null && conditionData.length() > threshold) {
            return true;
        }
        return false;
    }

    /**
     * Process timeout scenario - for timeout testing
     */
    public String processWithTimeout(String data, Integer timeoutMs) throws InterruptedException {
        System.out.println("Processing with timeout: data=" + data + ", timeout=" + timeoutMs + "ms");
        if (timeoutMs != null && timeoutMs > 0) {
            Thread.sleep(timeoutMs);
        }
        return "timeout_processed_" + data;
    }

    /**
     * Simulate retry scenario - for retry testing
     */
    public String processWithRetry(String data, Integer attemptCount) {
        System.out.println("Processing with retry: data=" + data + ", attempt=" + attemptCount);
        if (attemptCount != null && attemptCount < 3) {
            throw new RuntimeException("Retry attempt " + attemptCount + " failed");
        }
        return "retry_success_" + data;
    }

}
